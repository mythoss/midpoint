/*
 * Copyright (c) 2010-2018 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.gui.impl.prism;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.prism.delta.DeltaFactory;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;

import com.evolveum.midpoint.common.refinery.RefinedObjectClassDefinition;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.prism.ItemWrapperOld;
import com.evolveum.midpoint.gui.api.prism.PrismObjectWrapper;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.ItemProcessing;
import com.evolveum.midpoint.prism.MutableItemDefinition;
import com.evolveum.midpoint.prism.OriginType;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.PrismReferenceValue;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.Revivable;
import com.evolveum.midpoint.prism.Visitor;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.prism.ContainerStatus;
import com.evolveum.midpoint.web.component.prism.ContainerValueWrapper;
import com.evolveum.midpoint.web.component.prism.HeaderStatus;
import com.evolveum.midpoint.web.component.prism.ItemWrapperComparator;
import com.evolveum.midpoint.web.component.prism.PrismWrapper;
import com.evolveum.midpoint.web.component.prism.ValueWrapperOld;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.CapabilityType;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;

/**
 * @author lazyman
 * @author katkav
 */
public class ObjectWrapperOld<O extends ObjectType> extends PrismWrapper implements Serializable, Revivable, DebugDumpable {
	private static final long serialVersionUID = 1L;

	public static final String F_DISPLAY_NAME = "displayName";
	public static final String F_SELECTED = "selected";
	public static final String F_OBJECT = "object";

	private static final Trace LOGGER = TraceManager.getTrace(ObjectWrapperOld.class);

	public static final String PROPERTY_CONTAINERS = "containers";

	private PrismObject<O> object;
	private PrismObject<O> objectOld;
	private ObjectDelta<O> oldDelta;
	private ContainerStatus status;
	private HeaderStatus headerStatus;
	private String displayName;
	private String description;
	private List<ContainerWrapperImpl<? extends Containerable>> containers;

	private boolean selectable;
	private boolean selected;

	private boolean showAssignments = false;
	// whether to show name and description properties and metadata container
	private boolean showInheritedObjectAttributes = true;

	// readonly flag is an override. false means "do not override"
	private boolean readonly = false;

	// whether to make wicket enforce that required fields are filled-in (if set
	// to false, this check has to be done explicitly)
	private boolean enforceRequiredFields = true;

	private Collection<SelectorOptions<GetOperationOptions>> loadOptions;
	private OperationResult result;

	private Collection<PrismObject<OrgType>> parentOrgs = new ArrayList<>();

	private OperationResult fetchResult;
	// a "static" (non-refined) definition that reflects editability of the
	// object in terms of midPoint schema limitations and security
	// private PrismContainerDefinition objectDefinitionForEditing;
	// a refined definition of an resource object class that reflects its
	// editability; applicable for shadows only
	private RefinedObjectClassDefinition objectClassDefinitionForEditing;

	public ObjectWrapperOld(String dispayName, String descritpion, PrismObject<O> object, ContainerStatus status) {
		Validate.notNull(object, "Object must not be null.");
		Validate.notNull(status, "Container status must not be null.");

		this.displayName = displayName;
		this.description = description;
		this.object = object;
		this.objectOld = object.clone();
		this.status = status;
	}

	public void revive(PrismContext prismContext) throws SchemaException {
		if (object != null) {
			object.revive(prismContext);
		}
		if (oldDelta != null) {
			oldDelta.revive(prismContext);
		}
		if (containers != null) {
			for (ContainerWrapperImpl containerWrapper : containers) {
				containerWrapper.revive(prismContext);
			}
		}
	}

	public Collection<PrismObject<OrgType>> getParentOrgs() {
		return parentOrgs;
	}

	public OperationResult getFetchResult() {
		return fetchResult;
	}

	public void setFetchResult(OperationResult fetchResult) {
		this.fetchResult = fetchResult;
	}

	void setResult(OperationResult result) {
		this.result = result;
	}

	public OperationResult getResult() {
		return result;
	}

	public void clearResult() {
		result = null;
	}

	public Collection<SelectorOptions<GetOperationOptions>> getLoadOptions() {
		return loadOptions;
	}

	public void setLoadOptions(Collection<SelectorOptions<GetOperationOptions>> loadOptions) {
		this.loadOptions = loadOptions;
	}

	public HeaderStatus getHeaderStatus() {
		if (headerStatus == null) {
			headerStatus = HeaderStatus.NORMAL;
		}
		return headerStatus;
	}

	public ObjectDelta<O> getOldDelta() {
		return oldDelta;
	}

	public void setOldDelta(ObjectDelta<O> oldDelta) {
		this.oldDelta = oldDelta;
	}

	public void setHeaderStatus(HeaderStatus headerStatus) {
		this.headerStatus = headerStatus;
	}

	public PrismObject<O> getObject() {
		return object;
	}

	public PrismObject<O> getObjectOld() {
		return objectOld;
	}

	public String getDisplayName() {
		if (displayName == null) {
			return WebComponentUtil.getDisplayNameOrName(object);
		}
		return displayName;
	}

	public ContainerStatus getStatus() {
		return status;
	}

	public String getDescription() {
		return description;
	}

	public boolean isSelectable() {
		return selectable;
	}

	public void setSelectable(boolean selectable) {
		this.selectable = selectable;
	}

	public boolean isSelected() {
		return selected;
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
	}

	public <C extends Containerable> List<ContainerWrapperImpl<? extends Containerable>> getContainers() {
		if (containers == null) {
			containers = new ArrayList<>();
		}
		return containers;
	}

	public void setContainers(List<ContainerWrapperImpl<? extends Containerable>> containers) {
		this.containers = containers;
	}

	public <C extends Containerable> ContainerWrapperImpl<C> findContainerWrapper(ItemPath path) {
		if (path == null || path.isEmpty()) {
			return (ContainerWrapperImpl<C>) findMainContainerWrapper();

		}

		if (path.size() == 1) {
			return (ContainerWrapperImpl<C>) getContainers().stream().filter(wrapper -> path.equivalent(wrapper.getPath()))
					.findFirst().orElse(null);
		}

		ContainerWrapperImpl<C> containerWrapper = findContainerWrapper(path.firstAsPath());
		if (containerWrapper == null) {
			return null;
		}

		return containerWrapper.findContainerWrapper(path);

	}

	public <C extends Containerable> ContainerValueWrapper<C> findContainerValueWrapper(ItemPath path) {
		if (path == null || path.isEmpty()) {
			ContainerWrapperImpl<C> mainContainer = (ContainerWrapperImpl<C>) findMainContainerWrapper();
			if (mainContainer == null) {
				return null;
			}
			return mainContainer.getValues().iterator().next();
		}

		ContainerWrapperImpl<C> containerWrapper = findContainerWrapper(path.firstAsPath());
		if (containerWrapper == null) {
			return null;
		}

		return containerWrapper.findContainerValueWrapper(path);

	}

	public ContainerWrapperImpl<O> findMainContainerWrapper() {
		for (ContainerWrapperImpl wrapper : getContainers()) {
			if (wrapper.isMain()) {
				return wrapper;
			}
		}
		return null;
	}

	public <IW extends ItemWrapperOld> IW findPropertyWrapper(ItemPath path) {
		ContainerWrapperImpl containerWrapper;
		ItemPath propertyPath;
		if (path.size() == 1) {
			containerWrapper = findMainContainerWrapper();
			propertyPath = path;
		} else {
			containerWrapper = findContainerWrapper(path.firstAsPath());
			propertyPath = path.rest();
		}
		if (containerWrapper == null) {
			return null;
		}
		return (IW) containerWrapper.findPropertyWrapper(ItemPath.firstToNameOrNull(propertyPath));
	}

	public void normalize() throws SchemaException {
		ObjectDelta delta = getObjectDelta();
		if (ChangeType.ADD.equals(delta.getChangeType())) {
			object = delta.getObjectToAdd();
		} else {
			delta.applyTo(object);
		}
	}

	public void sort() {
		getContainers().forEach(ContainerWrapper -> ContainerWrapper.sort());
		computeStripes();
	}

	@Override
	public void computeStripes() {
		getContainers().forEach(c -> c.computeStripes());
	}

	public ObjectDelta<O> getObjectDelta() throws SchemaException {
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("Wrapper before creating delta:\n{}", this.debugDump());
		}

		ObjectDelta<O> delta = object.getPrismContext().deltaFactory().object().create(object.getCompileTimeClass(), ChangeType.MODIFY);
		delta.setOid(object.getOid());

		List<ContainerWrapperImpl<? extends Containerable>> containers = getContainers();
		// sort containers by path size
		Collections.sort(containers, new PathSizeComparator());

		for (ContainerWrapperImpl containerWrapper : getContainers()) {
			containerWrapper.collectModifications(delta);
		}
		
		if (ContainerStatus.ADDING.equals(getStatus())) {
			PrismObject<O> objectToModify = object.clone();
			delta.applyTo(objectToModify);
			cleanupEmptyContainers(objectToModify);
			ObjectDelta<O> addDelta = DeltaFactory.Object.createAddDelta(objectToModify);
			if (object.getPrismContext() != null) {
				// Make sure we have all the definitions
				object.getPrismContext().adopt(delta);
			}
			return addDelta;
			
		}
		// returning container to previous order
		//Collections.sort(containers, new ItemWrapperComparator());

		if (object.getPrismContext() != null) {
			// Make sure we have all the definitions
			object.getPrismContext().adopt(delta);
		}

		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("Creating delta from wrapper {}: existing object, creating delta:\n{}", this, delta.debugDump());
		}

		return delta;
	}

	// TODO move to appropriate place!
	public static PrismValue clone(PrismValue value) {
		if (value == null) {
			return null;
		}
		PrismValue cloned = value.clone();
		cloned.setOriginType(OriginType.USER_ACTION);
		if (value instanceof PrismPropertyValue) {
			PrismPropertyValue ppValue = (PrismPropertyValue) value;
			if (ppValue.getValue() instanceof ProtectedStringType) {
				((PrismPropertyValue) cloned).setValue(((ProtectedStringType) ppValue.getValue()).clone());
			}
			if (ppValue.getValue() instanceof PolyString) {
				PolyString poly = (PolyString) ppValue.getValue();
				if (StringUtils.isEmpty(poly.getOrig())) {
					return null;
				}
				((PrismPropertyValue) cloned).setValue(new PolyString(poly.getOrig(), poly.getNorm()));
			}
		} else if (value instanceof PrismReferenceValue) {
			if (cloned == null) {
				return null;
			}
			if (cloned.isEmpty()) {
				return null;
			}
		}

		return cloned;
	}

	protected boolean hasResourceCapability(ResourceType resource, Class<? extends CapabilityType> capabilityClass) {
		if (resource == null) {
			return false;
		}
		return ResourceTypeUtil.hasEffectiveCapability(resource, capabilityClass);
	}

	private void cleanupEmptyContainers(PrismContainer container) {
		List<PrismContainerValue> values = container.getValues();
		List<PrismContainerValue> valuesToBeRemoved = new ArrayList<>();
		for (PrismContainerValue value : values) {
			List<? extends Item> items = value.getItems();
			if (items != null) {
				Iterator<? extends Item> iterator = items.iterator();
				while (iterator.hasNext()) {
					Item item = iterator.next();

					if (item instanceof PrismContainer) {
						cleanupEmptyContainers((PrismContainer) item);

						if (item.isEmpty()) {
							iterator.remove();
						}
					}
				}
			}

			if (items == null || value.isEmpty()) {
				valuesToBeRemoved.add(value);
			}
		}

		container.removeAll(valuesToBeRemoved);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ObjectWrapper(");
		if (displayName == null) {
			if (object == null) {
				builder.append("null");
			} else {
				builder.append(object.getElementName());
			}
		} else {
			builder.append(displayName);
		}
		builder.append(" (");
		builder.append(status);
		builder.append(") ");
		builder.append(getContainers() == null ? null : getContainers().size());
		builder.append(" containers)");
		return builder.toString();
	}

	public boolean isProtectedAccount() {
		if (object == null || !(ShadowType.class.isAssignableFrom(object.getCompileTimeClass()))) {
			return false;
		}

		PrismProperty<Boolean> protectedObject = object.findProperty(ShadowType.F_PROTECTED_OBJECT);
		if (protectedObject == null) {
			return false;
		}

		return protectedObject.getRealValue() != null ? protectedObject.getRealValue() : false;
	}

	private static class PathSizeComparator implements Comparator<ContainerWrapperImpl> {

		@Override
		public int compare(ContainerWrapperImpl c1, ContainerWrapperImpl c2) {
			int size1 = c1.getPath() != null ? c1.getPath().size() : 0;
			int size2 = c2.getPath() != null ? c2.getPath().size() : 0;

			return size1 - size2;
		}
	}

	public String getOid() {
		return object.getOid();
	}

	public boolean isShowAssignments() {
		return showAssignments;
	}

	public void setShowAssignments(boolean showAssignments) {
		this.showAssignments = showAssignments;
	}

	@Override
	public void setShowEmpty(boolean showEmpty, boolean recursive) {
		super.setShowEmpty(showEmpty, recursive);
	}

	public void setShowEmpty(boolean showEmpty) {
		super.setShowEmpty(showEmpty, false);
		getContainers().forEach(container -> container.setShowEmpty(showEmpty, true));
	}

	public boolean isReadonly() {
		if (isProtectedAccount()) {
			return true;
		}
		return readonly;
	}

	public void setReadonly(boolean readonly) {
		this.readonly = readonly;
	}

	public boolean isShowInheritedObjectAttributes() {
		return showInheritedObjectAttributes;
	}

	public void setShowInheritedObjectAttributes(boolean showInheritedObjectAttributes) {
		this.showInheritedObjectAttributes = showInheritedObjectAttributes;
	}

	public boolean isEnforceRequiredFields() {
		return enforceRequiredFields;
	}

	public void setEnforceRequiredFields(boolean enforceRequiredFields) {
		this.enforceRequiredFields = enforceRequiredFields;
	}

	public PrismContainerDefinition getDefinition() {
		return object.getDefinition();
	}

	public PrismContainerDefinition getRefinedAttributeDefinition() {
		if (objectClassDefinitionForEditing != null) {
			return objectClassDefinitionForEditing.toResourceAttributeContainerDefinition();
		}
		return null;
	}

	public void copyRuntimeStateTo(ObjectWrapperOld<O> newWrapper) {
		newWrapper.setMinimalized(this.isMinimalized());
		// newWrapper.setShowEmpty(this.isShowEmpty());
		newWrapper.setSorted(this.isSorted());
		newWrapper.setSelectable(this.isSelectable());
		newWrapper.setSelected(this.isSelected());
		newWrapper.setShowAssignments(this.isShowAssignments());
		newWrapper.setShowInheritedObjectAttributes(this.isShowInheritedObjectAttributes());
		newWrapper.setReadonly(this.isReadonly());
	}

	/**
	 * To be used when enforceRequiredFields is false, so we want to check them
	 * only when really needed (e.g. MID-3876: when rejecting a work item,
	 * fields marked as required need not be present).
	 *
	 * Currently unused; we use DynamicFormPanel.checkRequiredFields instead. Might be
	 * useful in the future.
	 *
	 * @return true if everything is OK
	 */
	@SuppressWarnings("unused")
	public boolean checkRequiredFields(PageBase pageBase) {
		boolean rv = true;
		for (ContainerWrapperImpl<? extends Containerable> container : containers) {
			if (!container.checkRequired(pageBase)) {
				rv = false; // continuing to display messages for all missing fields
			}
		}
		return rv;
	}

	@Override
	public String debugDump(int indent) {
		StringBuilder sb = new StringBuilder();
		DebugUtil.indentDebugDump(sb, indent);
		sb.append("ObjectWrapper:\n");
		DebugUtil.debugDumpWithLabelLn(sb, "displayName", displayName, indent + 1);
		DebugUtil.debugDumpWithLabelLn(sb, "description", description, indent + 1);
		DebugUtil.debugDumpWithLabelLn(sb, "object", object == null ? null : object.toString(), indent + 1);
		DebugUtil.debugDumpWithLabelLn(sb, "objectOld", objectOld == null ? null : objectOld.toString(), indent + 1);
		DebugUtil.debugDumpWithLabelLn(sb, "oldDelta", oldDelta, indent + 1);
		DebugUtil.debugDumpWithLabelLn(sb, "status", status == null ? null : status.toString(), indent + 1);
		DebugUtil.debugDumpWithLabelLn(sb, "headerStatus", headerStatus == null ? null : headerStatus.toString(), indent + 1);
		DebugUtil.debugDumpWithLabelLn(sb, "loadOptions", loadOptions, indent + 1);
		DebugUtil.debugDumpWithLabel(sb, "containers", containers, indent + 1);
		return sb.toString();
	}

	
}