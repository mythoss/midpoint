package com.evolveum.midpoint.web.component.assignment;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.web.component.prism.ContainerWrapper;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AbstractPolicyConstraintType;
import org.apache.wicket.model.IModel;

/**
 * Created by honchar
 */
public class PolicyRuleConstraintsExpandablePanel<P extends AbstractPolicyConstraintType> extends BasePanel<P>{
    private static final String ID_BOX_TITLE = "boxTitle";
    private static final String ID_REMOVE_BUTTON = "removeButton";
    private static final String ID_PROPERTIES_CONTAINER = "propertiesContainer";

    private ContainerWrapper policyRuleConstraintsContainerWrapper;

    public PolicyRuleConstraintsExpandablePanel(String id, IModel<P> model){
        super(id, model);
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
//        initContainerWrapper();
        initLayout();
    }

//    private void initContainerWrapper(){
//        ContainerWrapperFactory cwf = new ContainerWrapperFactory(getPageBase());
//        ItemPath exclusionContainerPath = ItemPath.create(AssignmentType.F_POLICY_RULE, PolicyRuleType.F_POLICY_CONSTRAINTS,
//                PolicyConstraintsType.F_EXCLUSION);
//
////                    if (exclusionContainer != null) {
//        policyRuleConstraintsContainerWrapper = cwf.createContainerWrapper(getModelObject().asPrismContainerValue().getContainer(), ContainerStatus.MODIFYING, exclusionContainerPath, true);
////                    } else {
////                    exclusionContainer = containerDef.instantiate();
////                        containerWrapper = cwf.createContainerWrapper(exclusionContainer, ContainerStatus.ADDING, exclusionContainerPath, false);
////                    }
//
//    }

    private void initLayout(){
//        Label boxTitle = new Label(ID_BOX_TITLE, getModel().getObject().asPrismContainerValue().getPath().last());
//        add(boxTitle);
//
//        AjaxButton removeRowButton = new AjaxButton(ID_REMOVE_BUTTON) {
//            @Override
//            public void onClick(AjaxRequestTarget ajaxRequestTarget) {
////                policyRuleConstraintsContainerWrapper.getStatus().
//            }
//        };
//        add(removeRowButton);
//        
//        
//        PrismContainerPanel propertiesPanel = new PrismContainerPanel(ID_PROPERTIES_CONTAINER,
//                Model.of(policyRuleConstraintsContainerWrapper), false, null, getPageBase());
//        propertiesPanel.setOutputMarkupId(true);
//        add(propertiesPanel);

    }
}
