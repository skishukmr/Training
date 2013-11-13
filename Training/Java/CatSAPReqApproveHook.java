/*
*
*  Author : James Suresh Pagadala
*
*  Date : Nov 18 2008
*
*  Description : Performing budget check when CBS Capital Approver trying to approve requisition.
*  Revision History
*  17/10/2013 IBM_AMS_MANOJ RSD  Addition of CBS budget check warning codes
*
*/

package config.java.hook.sap;

import java.util.List;
import java.util.Map;

import ariba.approvable.core.Approvable;
import ariba.approvable.core.ApprovableHook;
import ariba.base.core.Base;
import ariba.base.core.BaseId;
import ariba.base.core.ClusterRoot;
import ariba.base.core.aql.AQLOptions;
import ariba.base.core.aql.AQLQuery;
import ariba.base.core.aql.AQLResultCollection;
import ariba.purchasing.core.Requisition;
import ariba.user.core.Role;
import ariba.user.core.User;
import ariba.util.core.Constants;
import ariba.util.core.FastStringBuffer;
import ariba.util.core.ListUtil;
import ariba.util.core.MapUtil;
import ariba.util.log.Log;
import config.java.common.sap.BudgetChkResp;
import config.java.common.sap.CATBudgetCheck;
import config.java.common.sap.CATSAPConstants;
import config.java.common.sap.CATSAPUtils;


public class CatSAPReqApproveHook implements ApprovableHook
{

    FastStringBuffer totalMsg = new FastStringBuffer ();
	boolean hasErrors = false;

    public List run(Approvable approvable)
    {
		Log.customer.debug("CatSAPReqApproveHook : run : ***START***");
        if(! (approvable instanceof Requisition))
        {
			Log.customer.debug("CatSAPReqApproveHook : run : approvable is not instance of Requisition " + approvable);
			return NoErrorResult;
		}

        Requisition requisition = (Requisition)approvable;
        ClusterRoot user = Base.getSession().getEffectiveUser();
        User approver = (User)user;

		Log.customer.debug("CatSAPReqApproveHook : run : approver : " + approver);

		Log.customer.debug("CatSAPReqApproveHook : run : requisition : " + requisition);


		if (CATSAPUtils.wasReqERFQ(requisition)&& approver.hasPermission("CatPurchasing"))
		{
			// Santanu : Accounting Validation if it was RFQ and User is Buyer
			String reqresult = CATSAPUtils.checkAccounting(requisition);
			if (!reqresult.equals("0")) {
				//String formatLineError = Fmt.S(lineresult, errorLine);
				hasErrors = true;
				totalMsg.append(reqresult);
				Log.customer.debug("%s *** Req Error Msg: %s", "CatSAPReqApproveHook", totalMsg.toString());
			}
			if(hasErrors){
				return ListUtil.list(Constants.getInteger(-1), totalMsg.toString());
			}
			// Santanu : Accounting Validation if it was RFQ and User is Buyer

		}


		// Santanu : Do BudgetCheck when Buyer changed eRFQ to Normal Req
		if((approver.hasPermission("CatPurchasing") && CATSAPUtils.wasReqERFQ(requisition)) || isCapitalApprover(requisition, approver)){

			Log.customer.debug("CatSAPReqApproveHook : run : Capital Approver " );
			BudgetChkResp budgetChkResp = null;
			CATBudgetCheck catBudgetCheck = new CATBudgetCheck();

			try {
				Log.customer.debug("CatSAPReqApproveHook : run : Before perfoming budget check " );
				budgetChkResp = catBudgetCheck.performBudgetCheck(requisition);
				Log.customer.debug("CatSAPReqApproveHook : run : After perfoming budget check " );
			}
			catch(Exception excp){
				Log.customer.debug("CatSAPReqApproveHook : run : excp " + excp);
				return ListUtil.list(Constants.getInteger(-1), "BudgetCheck Processing Error. Please contact System Administrator");
			}
                        //If the IO reaches less than 90 % CBS Budget check code = 000- "it allows the Capital Approver to approve the PR"
                        //Added two new codes for warning--011 - If it reaches 90% and < 100%---    011  - Warning and "it allows the Capital Approver to approve the PR"
                       //Added two new codes for warning--012 -  If it reaches 100% and <= 105 %---   012 -  Warning ( Note: At 105% also it sends 012) "it allows the Capital Approver to approve the PR"

			String BUDGET_CHECK_PASS_CODE = "000";
                        String BUDGET_CHECK_PASS_CODE1 = "011";
			String BUDGET_CHECK_PASS_CODE2 = "012";

			if (budgetChkResp == null){

				Log.customer.debug("CatSAPReqApproveHook : run : Budget Check response is null " );
				return ListUtil.list(Constants.getInteger(-1), "BudgetCheck response is null. Please contact System Administrator");
			}
                        //Changed as per CBS BudgetCheck requirement to set it up as warning!!
			if ((BUDGET_CHECK_PASS_CODE1.equals(budgetChkResp.getBudgetCheckMsgCode())) || (BUDGET_CHECK_PASS_CODE2.equals(budgetChkResp.getBudgetCheckMsgCode())) )
			{
				return ListUtil.list(Constants.getInteger(1), budgetChkResp.getBudgetCheckMsgTxt());
			}
                       //Added two new codes for warning--Code ends

			if(!(BUDGET_CHECK_PASS_CODE.equals(budgetChkResp.getBudgetCheckMsgCode()))){
				Log.customer.debug("CatSAPReqApproveHook : run : Budget Check error from MACH1 / CBS " );
				Log.customer.debug("CatSAPReqApproveHook : run : Budget Check Error Code "  + budgetChkResp.getBudgetCheckMsgCode() );
				Log.customer.debug("CatSAPReqApproveHook : run : Budget Check Message Text "  + budgetChkResp.getBudgetCheckMsgTxt() );
				return ListUtil.list(Constants.getInteger(-1), budgetChkResp.getBudgetCheckMsgTxt());
			}
		}

		Log.customer.debug("CatSAPReqApproveHook : run : ***END***");
        return NoErrorResult;
    }

    public CatSAPReqApproveHook()
    {
    }

    private boolean isCapitalApprover(Requisition req, User approver){

		Log.customer.debug("CatSAPReqApproveHook : isCapitalApprover : ****START**** ");
		String CAPITAL_ROLE_STARTS_WITH = CATSAPConstants.getSAPConstants().getCapitalRoleStartsWith();
		Log.customer.debug("CatSAPReqApproveHook : isCapilatApproverIter : CAPITAL_ROLE_STARTS_WITH " + CAPITAL_ROLE_STARTS_WITH);
		int READY_STATE = 2;

		//String capitalAppQry = "select ar.Approver from Requisition join ApprovalRequest ar using this.ApprovalRequests join ariba.user.core.Role role1 //using ar.Approver where ar.State=2 and role1.UniqueName like 'CP_%' and this.UniqueName = 'PR11063'";
		String capitalAppQry = "select ar.Approver from ariba.purchasing.core.Requisition join ApprovalRequest ar using this.ApprovalRequests join ariba.user.core.Role role1 using ar.Approver where ar.State= :readyState and role1.UniqueName like :CapitalRoleStartsWith and this.UniqueName = :ReqID";

		Map bindVars = MapUtil.map();
		bindVars.put("readyState", new Integer(READY_STATE));
		bindVars.put("CapitalRoleStartsWith", CAPITAL_ROLE_STARTS_WITH.concat("%"));
		bindVars.put("ReqID", req.getUniqueName() );

		AQLOptions options = new AQLOptions(req.getPartition());
		options.setActualParameters(bindVars);
		AQLQuery query = AQLQuery.parseQuery(capitalAppQry);

		Log.customer.debug("CatSAPReqApproveHook: isCapitalApprover : query " + query);

		AQLResultCollection results = Base.getService().executeQuery(query,
				options);

		if (results.getErrors() != null) {
			Log.customer.debug(results.getFirstError().toString());
			return false;
		}

		if (results.next()) {
			Log.customer.debug("CatSAPReqApproveHook: isCapitalApprover : Results Processing ");

			BaseId tmpBaseID = results.getBaseId(0);

			Log.customer.debug("CatSAPReqApproveHook: isCapitalApprover : tmpBaseID " + tmpBaseID);

			if(tmpBaseID == null){
				Log.customer.debug("CatSAPReqApproveHook: isCapitalApprover : tmpBaseID is null");
				return false;
			}

                // S. Sato - AUL -
                //           Modified code for the 9r API. Added check for ClassCastException.
                //           Commenting out the old 822 code
            // Role capitalRole = (Role) (Base.getService().objectIfAny(tmpBaseID));
			ClusterRoot cr = tmpBaseID.get();
			if (!(cr instanceof Role)) {
				Log.customer.debug("CatSAPReqApproveHook: tmpBaseID is not of type Role");
				return false;
			}
			Role capitalRole = (Role) cr;

			Log.customer.debug("CatSAPReqApproveHook: isCapitalApprover : capitalRole " + capitalRole);

			if(capitalRole == null){
				Log.customer.debug("CatSAPReqApproveHook: isCapitalApprover : capitalRole is null" );
				return false;
			}

			if(approver.hasRole(capitalRole)){
				Log.customer.debug("CatSAPReqApproveHook: isCapitalApprover : User is Capital Approver" );
				return true;
			}
		}

		Log.customer.debug("CatSAPReqApproveHook : isCapitalApprover : ****END**** ");

		return false;
	}

    private static final List NoErrorResult = ListUtil.list(Constants.getInteger(0));
    private static final String THISCLASS = "CatSAPReqApproveHook";
}
