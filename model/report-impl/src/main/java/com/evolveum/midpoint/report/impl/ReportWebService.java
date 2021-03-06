/*
 * Copyright (c) 2010-2019 Evolveum
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
package com.evolveum.midpoint.report.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.xml.ns._public.common.common_3.SelectorQualifiedGetOptionsType;

import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.interceptor.Fault;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.evolveum.midpoint.audit.api.AuditEventRecord;
import com.evolveum.midpoint.model.common.util.AbstractModelWebService;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.report.api.ReportPort;
import com.evolveum.midpoint.report.api.ReportService;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.api_types_3.ObjectListType;
import com.evolveum.midpoint.xml.ns._public.common.audit_3.AuditEventRecordListType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ReportParameterType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ReportType;
import com.evolveum.midpoint.xml.ns._public.report.report_3.RemoteReportParameterType;
import com.evolveum.midpoint.xml.ns._public.report.report_3.RemoteReportParametersType;
import com.evolveum.midpoint.xml.ns._public.report.report_3.ReportPortType;

@Service
public class ReportWebService extends AbstractModelWebService implements ReportPortType, ReportPort {

	private static final String OP_EVALUATE_SCRIPT = ReportWebService.class.getName() + ".evaluateScript";
	private static final String OP_EVALUATE_AUDIT_SCRIPT = ReportWebService.class.getName() + ".evaluateAuditScript";
	private static final String OP_PROCESS_REPORT = ReportWebService.class.getName() + ".processReport";

	private static transient Trace LOGGER = TraceManager.getTrace(ReportWebService.class);

	@Autowired private PrismContext prismContext;
	@Autowired private ReportService reportService;

	@Override
	public ObjectListType evaluateScript(String reportOid, String script, RemoteReportParametersType parameters) {
		
		Task task = createTaskInstance(OP_EVALUATE_SCRIPT);
		auditLogin(task);
		OperationResult operationResult = task.getResult();
		
		try {
			
			PrismObject<ReportType> report = authorizeReportProcessing(reportOid, task, operationResult);
			
			VariablesMap params = getParamsMap(parameters);
			Collection resultList = reportService.evaluateScript(report, script, params, task, operationResult);
			return createObjectListType(resultList);
		} catch (Throwable e) {
			throw new Fault(e);
		}

	}

	@Override
	public AuditEventRecordListType evaluateAuditScript(String reportOid, String script, RemoteReportParametersType parameters) {
		
		Task task = createTaskInstance(OP_EVALUATE_AUDIT_SCRIPT);
		auditLogin(task);
		OperationResult operationResult = task.getResult();

		try {
			PrismObject<ReportType> report = authorizeReportProcessing(reportOid, task, operationResult);
			
			VariablesMap params = getParamsMap(parameters);
			Collection<AuditEventRecord> resultList = reportService.evaluateAuditScript(report, script, params, task, operationResult);
			return createAuditEventRecordListType(resultList);
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			throw new Fault(e);
		}

	}

	private VariablesMap getParamsMap(RemoteReportParametersType parametersType) throws SchemaException {

		prismContext.adopt(parametersType);
		VariablesMap parametersMap = new VariablesMap();
		if (parametersType == null || parametersType.getRemoteParameter() == null
				|| parametersType.getRemoteParameter().isEmpty()) {
			return parametersMap;
		}
		List<RemoteReportParameterType> items = parametersType.getRemoteParameter();
		for (RemoteReportParameterType item : items) {
			String paramName = item.getParameterName();
			ReportParameterType param = item.getParameterValue();
			if (param == null){
				parametersMap.put(paramName, null);
				continue;
			}
			if (param.getAny().size() == 1) {
				parametersMap.put(paramName, param.getAny().get(0), param.getAny().get(0).getClass());
			} else {
				parametersMap.put(paramName, param.getAny(), List.class);
			}

		}

		return parametersMap;


	}

	private ObjectListType createObjectListType(Collection resultList) {
		if (resultList == null) {
			return new ObjectListType();
		}

		ObjectListType results = new ObjectListType();
		int skipped = 0;
		for (Object object : resultList) {
			if (object instanceof PrismObject) {
				results.getObject().add(((PrismObject<ObjectType>) object).asObjectable());
			} else if (object instanceof ObjectType) {
				results.getObject().add((ObjectType) object);
			} else {
				skipped++;
			}
		}
		if (skipped > 0) {
			LOGGER.warn("{} non-PrismObject data objects not returned, as these are not supported by ReportWebService yet", skipped);
		}

		return results;
	}

	private AuditEventRecordListType createAuditEventRecordListType(Collection<AuditEventRecord> resultList) {
		if (resultList == null) {
			return new AuditEventRecordListType();
		}

		AuditEventRecordListType results = new AuditEventRecordListType();
		for (AuditEventRecord auditRecord : resultList) {
			results.getObject().add(auditRecord.createAuditEventRecordType(true));
		}

		return results;
	}


	@Override
	public ObjectListType processReport(String reportOid, String query, RemoteReportParametersType parameters, SelectorQualifiedGetOptionsType options) {

		Task task = createTaskInstance(OP_PROCESS_REPORT);
		auditLogin(task);
		OperationResult operationResult = task.getResult();
		
		try {

			PrismObject<ReportType> report = authorizeReportProcessing(reportOid, task, operationResult);
			
			VariablesMap parametersMap = getParamsMap(parameters);
			ObjectQuery q = reportService.parseQuery(report, query, parametersMap, task, operationResult);
			Collection<PrismObject<? extends ObjectType>> resultList = reportService.searchObjects(q,
					MiscSchemaUtil.optionsTypeToOptions(options, prismContext), task, operationResult);

			return createObjectListType(resultList);
		} catch (SchemaException | ObjectNotFoundException | SecurityViolationException
				| CommunicationException | ConfigurationException | ExpressionEvaluationException e) {
			// TODO Auto-generated catch block
			throw new Fault(e);
		}

	}

	private PrismObject<ReportType> authorizeReportProcessing(String reportOid, Task task, OperationResult result) throws ObjectNotFoundException, SchemaException, SecurityViolationException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		if (StringUtils.isBlank(reportOid)) {
			throw new SchemaException("No report OID specified");
		}
		PrismObject<ReportType> report = reportService.getReportDefinition(reportOid, task, result);
		// TODO TODO TODO: authorization
		return report;
	}

}
