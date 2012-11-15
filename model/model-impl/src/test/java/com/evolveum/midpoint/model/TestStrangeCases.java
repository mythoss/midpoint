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
package com.evolveum.midpoint.model;

import static org.testng.AssertJUnit.assertNotNull;
import static com.evolveum.midpoint.test.IntegrationTestTools.display;
import static com.evolveum.midpoint.test.IntegrationTestTools.displayWhen;
import static com.evolveum.midpoint.test.IntegrationTestTools.displayThen;
import static com.evolveum.midpoint.test.IntegrationTestTools.displayTestTile;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.evolveum.icf.dummy.resource.DummyAccount;
import com.evolveum.midpoint.common.refinery.RefinedResourceSchema;
import com.evolveum.midpoint.common.refinery.ShadowDiscriminatorObjectDelta;
import com.evolveum.midpoint.model.AbstractInitializedModelIntegrationTest;
import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.model.api.PolicyViolationException;
import com.evolveum.midpoint.model.api.context.ModelContext;
import com.evolveum.midpoint.model.api.context.SynchronizationPolicyDecision;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismReference;
import com.evolveum.midpoint.prism.PrismReferenceValue;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.ReferenceDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.schema.ObjectOperationOption;
import com.evolveum.midpoint.schema.ObjectOperationOptions;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.holder.XPathHolder;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.schema.util.SchemaTestConstants;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.DummyAuditService;
import com.evolveum.midpoint.test.IntegrationTestTools;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ConsistencyViolationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.xml.ns._public.common.api_types_2.PropertyReferenceListType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.AccountConstructionType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.AccountShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.AccountSynchronizationSettingsType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.AssignmentPolicyEnforcementType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ConnectorConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.CredentialsType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.PasswordType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.UserType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ValuePolicyType;

/**
 * @author semancik
 *
 */
@ContextConfiguration(locations = {"classpath:application-context-model.xml",
        "classpath:application-context-repository.xml",
        "classpath:application-context-repo-cache.xml",
        "classpath:application-context-configuration-test.xml",
        "classpath:application-context-provisioning.xml",
        "classpath:application-context-task.xml",
		"classpath:application-context-audit.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class TestStrangeCases extends AbstractInitializedModelIntegrationTest {
	
	public static final File TEST_DIR = new File("src/test/resources/contract");
	
	public TestStrangeCases() throws JAXBException {
		super();
	}
	
	@Test
    public void test180DeleteHalfAssignmentFromUser() throws Exception {
		String TEST_NAME = "test180DeleteHalfAssignmentFromUser";
        displayTestTile(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(TestStrangeCases.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.FULL);
        dummyAuditService.clear();
        
        PrismObject<UserType> userOtis = getLeChuck();
        fillinUser(userOtis, "otis", "Otis");
        fillinUserAssignmentAccountConstruction(userOtis, RESOURCE_DUMMY_OID);
		
		display("Half-assigned user", userOtis);
		
		// Remember the assignment so we know what to remove
		AssignmentType assignmentType = userOtis.asObjectable().getAssignment().iterator().next();
		
		// Add to repo to avoid processing of the assignment
		String userOtisOid = repositoryService.addObject(userOtis, result);
        
        ObjectDelta<UserType> userDelta = ObjectDelta.createModificationReplaceContainer(UserType.class, 
        		userOtisOid, UserType.F_ASSIGNMENT, prismContext, assignmentType.asPrismContainerValue().clone());
        Collection<ObjectDelta<? extends ObjectType>> deltas = MiscSchemaUtil.createCollection(userDelta);
                
		// WHEN
        displayWhen(TEST_NAME);
		modelService.executeChanges(deltas, null, task, result);
		
		// THEN
		result.computeStatus();
        IntegrationTestTools.assertSuccess("executeChanges result", result);
        
		PrismObject<UserType> userOtisAfter = getUser(userOtisOid);
		assertNotNull("Otis is gone!", userOtisAfter);
		// Check accountRef
        assertUserNoAccountRefs(userOtisAfter);
        
        // Check if dummy resource account is gone
        assertNoDummyAccount("otis");
        
        // Check audit
        display("Audit", dummyAuditService);
        dummyAuditService.assertRecords(2);
        dummyAuditService.assertSimpleRecordSanity();
        dummyAuditService.assertAnyRequestDeltas();
        Collection<ObjectDelta<? extends ObjectType>> auditExecutionDeltas = dummyAuditService.getExecutionDeltas();
        assertEquals("Wrong number of execution deltas", 1, auditExecutionDeltas.size());
        PrismAsserts.asserHasDelta("Audit execution deltas", auditExecutionDeltas, ChangeType.MODIFY, UserType.class);
        dummyAuditService.assertExecutionSuccess();
	}
	
	@Test
    public void test190DeleteHalfAssignedUser() throws Exception {
		String TEST_NAME = "test190DeleteHalfAssignedUser";
        displayTestTile(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(TestStrangeCases.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.FULL);
        dummyAuditService.clear();
        
        PrismObject<UserType> userNavigator = getLeChuck();
        fillinUser(userNavigator, "navigator", "Head of the Navigator");
        fillinUserAssignmentAccountConstruction(userNavigator, RESOURCE_DUMMY_OID);
		
		display("Half-assigned user", userNavigator);
		
		// Add to repo to avoid processing of the assignment
		String userNavigatorOid = repositoryService.addObject(userNavigator, result);
        
        ObjectDelta<UserType> userDelta = ObjectDelta.createDeleteDelta(UserType.class, userNavigatorOid, prismContext);
        Collection<ObjectDelta<? extends ObjectType>> deltas = MiscSchemaUtil.createCollection(userDelta);
                
		// WHEN
        displayWhen(TEST_NAME);
		modelService.executeChanges(deltas, null, task, result);
		
		// THEN
		displayThen(TEST_NAME);
		result.computeStatus();
        IntegrationTestTools.assertSuccess("executeChanges result", result);
        
		try {
			getUser(userNavigatorOid);
			AssertJUnit.fail("navigator is still alive!");
		} catch (ObjectNotFoundException ex) {
			// This is OK
		}
        
        // Check if dummy resource account is gone
        assertNoDummyAccount("navigator");
        
        // Check audit
        display("Audit", dummyAuditService);
        dummyAuditService.assertRecords(2);
        dummyAuditService.assertSimpleRecordSanity();
        dummyAuditService.assertAnyRequestDeltas();
        Collection<ObjectDelta<? extends ObjectType>> auditExecutionDeltas = dummyAuditService.getExecutionDeltas();
        assertEquals("Wrong number of execution deltas", 1, auditExecutionDeltas.size());
        PrismAsserts.asserHasDelta("Audit execution deltas", auditExecutionDeltas, ChangeType.DELETE, UserType.class);
        dummyAuditService.assertExecutionSuccess();
	}	

}
