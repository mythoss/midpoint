/**
 * Copyright (c) 2011 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * Portions Copyrighted 2011 [name of copyright owner]
 */
package com.evolveum.midpoint.schema;

import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.validation.SchemaFactory;

import org.apache.xml.resolver.Catalog;
import org.apache.xml.resolver.CatalogManager;
import org.apache.xml.resolver.tools.CatalogResolver;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.exception.SchemaException;
import com.evolveum.midpoint.schema.exception.SystemException;
import com.evolveum.midpoint.schema.namespace.MidPointNamespacePrefixMapper;
import com.evolveum.midpoint.schema.processor.ComplexTypeDefinition;
import com.evolveum.midpoint.schema.processor.PropertyContainerDefinition;
import com.evolveum.midpoint.schema.processor.Schema;
import com.evolveum.midpoint.util.Dumpable;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.sun.xml.txw2.Document;

/**
 * Registry and resolver of schema files and resources.
 * 
 * 
 * @author Radovan Semancik
 *
 */
public class SchemaRegistry implements LSResourceResolver, EntityResolver, Dumpable {
	
	private javax.xml.validation.SchemaFactory schemaFactory;
	private javax.xml.validation.Schema javaxSchema;
	private EntityResolver builtinSchemaResolver;	
	private List<SchemaDescription> schemaDescriptions;
	private Map<String,SchemaDescription> parsedSchemas;
	private Map<String,PropertyContainerDefinition> extensionSchemas;
	private boolean initialized = false;
	
	private static final Trace LOGGER = TraceManager.getTrace(SchemaRegistry.class);
	
	public SchemaRegistry() {
		super();
		this.schemaDescriptions = new ArrayList<SchemaDescription>();
		this.parsedSchemas = new HashMap<String, SchemaDescription>();
		try {
			registerBuiltinSchemas();
		} catch (SchemaException e) {
			throw new SystemException("Built-in schema inconsistency: "+e.getMessage(),e);
		}
	}
	
	private void registerBuiltinSchemas() throws SchemaException {
		String prefix;
		prefix = MidPointNamespacePrefixMapper.getPreferredPrefix(SchemaConstants.NS_C);
		registerMidPointSchemaResource("xml/ns/public/common/common-1.xsd",prefix);
		prefix = MidPointNamespacePrefixMapper.getPreferredPrefix(SchemaConstants.NS_ANNOTATION);
		registerMidPointSchemaResource("xml/ns/public/common/annotation-1.xsd",prefix);
		prefix = MidPointNamespacePrefixMapper.getPreferredPrefix(SchemaConstants.NS_RESOURCE);
		registerMidPointSchemaResource("xml/ns/public/resource/resource-schema-1.xsd",prefix);
		prefix = MidPointNamespacePrefixMapper.getPreferredPrefix(SchemaConstants.NS_CAPABILITIES);
		registerMidPointSchemaResource("xml/ns/public/resource/capabilities-1.xsd",prefix);
		prefix = MidPointNamespacePrefixMapper.getPreferredPrefix(SchemaConstants.NS_ICF_CONFIGURATION);
		registerMidPointSchemaResource("xml/ns/public/connector/icf-1/connector-schema-1.xsd",prefix);
		prefix = MidPointNamespacePrefixMapper.getPreferredPrefix(SchemaConstants.NS_ICF_SCHEMA);
		registerMidPointSchemaResource("xml/ns/public/connector/icf-1/resource-schema-1.xsd",prefix);
		prefix = MidPointNamespacePrefixMapper.getPreferredPrefix(W3C_XML_SCHEMA_NS_URI);
		registerSchemaResource("xml/ns/standard/XMLSchema.xsd",prefix);
	}
	
	/**
	 * Must be called before call to initialize()
	 */
	public void registerSchemaResource(String resourcePath, String usualPrefix) throws SchemaException {
		SchemaDescription desc = SchemaDescription.parseResource(resourcePath);
		desc.setUsualPrefix(usualPrefix);
		schemaDescriptions.add(desc);
	}
	
	/**
	 * Must be called before call to initialize()
	 */
	public void registerMidPointSchemaResource(String resourcePath, String usualPrefix) throws SchemaException {
		SchemaDescription desc = SchemaDescription.parseResource(resourcePath);
		desc.setUsualPrefix(usualPrefix);
		desc.setMidPointSchema(true);
		schemaDescriptions.add(desc);
	}

	/**
	 * Must be called before call to initialize()
	 * @param node
	 */
	public void registerSchema(Node node, String sourceDescription) throws SchemaException {
		SchemaDescription desc = SchemaDescription.parseNode(node, sourceDescription);
		schemaDescriptions.add(desc);
	}

	/**
	 * Must be called before call to initialize()
	 * @param node
	 */
	public void registerSchema(Node node, String sourceDescription, String usualPrefix) throws SchemaException {
		SchemaDescription desc = SchemaDescription.parseNode(node, sourceDescription);
		desc.setUsualPrefix(usualPrefix);
		schemaDescriptions.add(desc);
	}
	
	public void registerMidPointSchemaFile(File file) throws FileNotFoundException, SchemaException {
		SchemaDescription desc = SchemaDescription.parseFile(file);
		desc.setMidPointSchema(true);
		schemaDescriptions.add(desc);
	}
	
	public void registerMidPointSchemasFromDirectory(File directory) throws FileNotFoundException, SchemaException {
		List<File> files = Arrays.asList(directory.listFiles());
		// Sort the filenames so we have deterministic order of loading
		// This is useful in tests but may come handy also during customization
		Collections.sort(files);
		for (File file: files) {
			if (file.getName().startsWith(".")) {
				// skip dotfiles. this will skip SVN data and similar things
				continue;
			}
			if (file.isDirectory()) {
				registerMidPointSchemasFromDirectory(file);
			}
			if (file.isFile()) {
				registerMidPointSchemaFile(file);
			}
		}
	}
	
	public void initialize() throws SAXException, IOException, SchemaException {
		try {
			
			initResolver();
			preParseSchemas();
			parseMidPointSchema();
			parseJavaxSchema();
			initialized = true;
			
		} catch (SAXException ex) {
			if (ex instanceof SAXParseException) {
				SAXParseException sex = (SAXParseException)ex;
				throw new SchemaException("Error parsing schema "+sex.getSystemId()+" line "+sex.getLineNumber()+": "+sex.getMessage());
			}
			throw ex;
		}
	}
	
	private void preParseSchemas() {
		for (SchemaDescription schemaDescription : schemaDescriptions) {	
			String namespace = schemaDescription.getNamespace();
			parsedSchemas.put(namespace, schemaDescription);
		}		
	}

	private void parseJavaxSchema() throws SAXException, IOException {
		schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		Source[] sources = new Source[schemaDescriptions.size()];
		int i = 0;
		for (SchemaDescription schemaDescription : schemaDescriptions) {
			Source source = schemaDescription.getSource();
			sources[i] = source;
			i++;
		}
		schemaFactory.setResourceResolver(this);
		javaxSchema = schemaFactory.newSchema(sources);
	}

	private void parseMidPointSchema() throws SchemaException {
		for (SchemaDescription schemaDescription : schemaDescriptions) {
			
			String namespace = schemaDescription.getNamespace();
			
			if (schemaDescription.isMidPointSchema()) {
				Element domElement = schemaDescription.getDomElement();
				Schema schema = Schema.parse(domElement, this);
				//Schema schema = Schema.parse(domElement);
				if (namespace == null) {
					namespace = schema.getNamespace();
				}
				LOGGER.trace("Parsed schema {}, namespace: {}",schemaDescription.getSourceDescription(),namespace);
				schemaDescription.setSchema(schema);
				detectExtensionSchema(schema);
			}
		}
	}
	
	private void detectExtensionSchema(Schema schema) {
		for (ComplexTypeDefinition def: schema.getDefinitions(ComplexTypeDefinition.class)) {
			
		}
		// TODO
	}

	private void initResolver() throws IOException {
		CatalogManager catalogManager = new CatalogManager();
		catalogManager.setUseStaticCatalog(true);
		catalogManager.setIgnoreMissingProperties(true);
		catalogManager.setVerbosity(1);
		catalogManager.setPreferPublic(true);
		CatalogResolver catalogResolver = new CatalogResolver(catalogManager);
		Catalog catalog = catalogResolver.getCatalog();

		Enumeration<URL> catalogs = Thread.currentThread().getContextClassLoader()
				.getResources("META-INF/catalog.xml");
		while (catalogs.hasMoreElements()) {
			URL catalogURL = catalogs.nextElement();
			catalog.parseCatalog(catalogURL);
		}
		
		builtinSchemaResolver=catalogResolver;
	}

	public javax.xml.validation.Schema getJavaxSchema() {
		return javaxSchema;
	}
	
	public Schema getSchema(String namespace) {
		return parsedSchemas.get(namespace).getSchema();
	}
	
	// Convenience and safety
	public Schema getCommonSchema() {
		if (!initialized) {
			throw new IllegalStateException("Attempt to get common schema from uninitialized Schema Registry");
		}
		return parsedSchemas.get(SchemaConstants.NS_C).getSchema();
	}
		
	private SchemaDescription lookupSchemaDescription(String namespace) {
		for (SchemaDescription desc : schemaDescriptions) {
			if (namespace.equals(desc.getNamespace())) {
				return desc;
			}
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.EntityResolver#resolveEntity(java.lang.String, java.lang.String)
	 */
	@Override
	public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
		InputSource inputSource = resolveResourceFromRegisteredSchemas(null, publicId, publicId, systemId, null);
		if (inputSource == null) {
			inputSource = resolveResourceUsingBuiltinResolver(null, null, publicId, systemId, null);
		}
		if (inputSource == null) {
			LOGGER.error("Unable to resolve resource with publicID: {}, systemID: {}",new Object[]{publicId, systemId});
			return null;
		}
		LOGGER.trace("Resolved resource with publicID: {}, systemID: {} : {}",new Object[]{publicId, systemId, inputSource});
		return inputSource;
	}

	
	/* (non-Javadoc)
	 * @see org.w3c.dom.ls.LSResourceResolver#resolveResource(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId,
			String baseURI) {
		InputSource inputSource = resolveResourceFromRegisteredSchemas(type,namespaceURI,publicId,systemId,baseURI);
		if (inputSource == null) {
			inputSource = resolveResourceUsingBuiltinResolver(type,namespaceURI,publicId,systemId,baseURI);
		}
		if (inputSource == null) {
			LOGGER.error("Unable to resolve resource of type {}, namespaceURI: {}, publicID: {}, systemID: {}, baseURI: {}",new Object[]{type, namespaceURI, publicId, systemId, baseURI});
			return null;
		}
		LOGGER.trace("Resolved resource of type {}, namespaceURI: {}, publicID: {}, systemID: {}, baseURI: {} : {}",new Object[]{type, namespaceURI, publicId, systemId, baseURI, inputSource});
		return new Input(publicId, systemId, inputSource.getByteStream());
	}
		
	private InputSource resolveResourceFromRegisteredSchemas(String type, String namespaceURI,
			String publicId, String systemId, String baseURI) {
		if (namespaceURI != null) {
			if (parsedSchemas.containsKey(namespaceURI)) {
				SchemaDescription schemaDescription = parsedSchemas.get(namespaceURI);
				if (schemaDescription.canInputStream()) {
					InputStream inputStream = schemaDescription.openInputStream();
					InputSource source = new InputSource();
					source.setByteStream(inputStream);
					//source.setSystemId(schemaDescription.getPath());
					source.setSystemId(systemId);
					source.setPublicId(publicId);
					return source;
				} else {
					throw new IllegalStateException("Requested resolution of schema "+schemaDescription.getSourceDescription()+" that does not support input stream");
				}
			}
		}
		return null;
	}

	public InputSource resolveResourceUsingBuiltinResolver(String type, String namespaceURI, String publicId, String systemId,
				String baseURI) {
		InputSource inputSource = null;
		try {
			if (namespaceURI != null) {
				// The systemId will be populated by schema location, not namespace URI.
				// As we use catalog resolver as the default one, we need to pass it the namespaceURI in place of systemId
				inputSource = builtinSchemaResolver.resolveEntity(publicId, namespaceURI);
			} else {
				inputSource = builtinSchemaResolver.resolveEntity(publicId, systemId);
			}
		} catch (SAXException e) {
			LOGGER.error("XML parser error resolving reference of type {}, namespaceURI: {}, publicID: {}, systemID: {}, baseURI: {}: {}",new Object[]{type, namespaceURI, publicId, systemId, baseURI, e.getMessage(), e});
			// TODO: better error handling
			return null;
		} catch (IOException e) {
			LOGGER.error("IO error resolving reference of type {}, namespaceURI: {}, publicID: {}, systemID: {}, baseURI: {}: {}",new Object[]{type, namespaceURI, publicId, systemId, baseURI, e.getMessage(), e});
			// TODO: better error handling
			return null;
		}		
		return inputSource;
	}
	
	class Input implements LSInput {

		private String publicId;
		private String systemId;
		private BufferedInputStream inputStream;

		public String getPublicId() {
		    return publicId;
		}

		public void setPublicId(String publicId) {
		    this.publicId = publicId;
		}

		public String getBaseURI() {
		    return null;
		}

		public InputStream getByteStream() {
		    return null;
		}

		public boolean getCertifiedText() {
		    return false;
		}

		public Reader getCharacterStream() {
		    return null;
		}

		public String getEncoding() {
		    return null;
		}

		public String getStringData() {
		    synchronized (inputStream) {
		        try {
		            byte[] input = new byte[inputStream.available()];
		            inputStream.read(input);
		            String contents = new String(input);
		            return contents;
		        } catch (IOException e) {
		        	LOGGER.error("IO error creating LSInput for publicID: {}, systemID: {}: {}",new Object[]{publicId, systemId, e.getMessage(), e});
		        	// TODO: better error handling
		            return null;
		        }
		    }
		}

		public void setBaseURI(String baseURI) {
		}

		public void setByteStream(InputStream byteStream) {
		}

		public void setCertifiedText(boolean certifiedText) {
		}

		public void setCharacterStream(Reader characterStream) {
		}

		public void setEncoding(String encoding) {
		}

		public void setStringData(String stringData) {
		}

		public String getSystemId() {
		    return systemId;
		}

		public void setSystemId(String systemId) {
		    this.systemId = systemId;
		}

		public BufferedInputStream getInputStream() {
		    return inputStream;
		}

		public void setInputStream(BufferedInputStream inputStream) {
		    this.inputStream = inputStream;
		}

		public Input(String publicId, String sysId, InputStream input) {
		    this.publicId = publicId;
		    this.systemId = sysId;
		    this.inputStream = new BufferedInputStream(input);
		}
	}

	/* (non-Javadoc)
	 * @see com.evolveum.midpoint.util.Dumpable#dump()
	 */
	@Override
	public String dump() {
		StringBuilder sb = new StringBuilder("SchemaRegistry:");
		
		sb.append("  Parsed Schemas:\n");
		for (String namespace: parsedSchemas.keySet()) {
			sb.append("    ");
			sb.append(namespace);
			sb.append(": ");
			sb.append(parsedSchemas.get(namespace).dump());
			sb.append("\n");
		}
		return sb.toString();
	}
	
}
