/**
 * Copyright (c) 2016-2018 Evolveum
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
package com.evolveum.midpoint.web.component;

import com.evolveum.midpoint.gui.api.util.ModelServiceLocator;
import com.evolveum.midpoint.model.api.authentication.CompiledUserProfile;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.web.model.ContainerableFromPrismObjectModel;
import com.evolveum.midpoint.xml.ns._public.common.common_3.GuiObjectDetailsPageType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SummaryPanelSpecificationType;

import org.apache.wicket.model.IModel;

public abstract class ObjectSummaryPanel<O extends ObjectType> extends AbstractSummaryPanel<O> {
	private static final long serialVersionUID = -3755521482914447912L;

	public ObjectSummaryPanel(String id, Class<O> type, final IModel<O> model, ModelServiceLocator serviceLocator) {
		super(id, model, determineConfig(type, serviceLocator.getCompiledUserProfile()));
	}

	private static <O extends ObjectType> SummaryPanelSpecificationType determineConfig(Class<O> type, CompiledUserProfile compiledUserProfile) {
		GuiObjectDetailsPageType guiObjectDetailsType = compiledUserProfile.findObjectDetailsConfiguration(type);
		if (guiObjectDetailsType == null) {
			return null;
		}
		return guiObjectDetailsType.getSummaryPanel();
	}
}
