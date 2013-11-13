/*

1. Created by James S Pagadala on Oct 06, 2008

2.		Sandeep Vaishnav		12/14/2011  Issue 219 : Added two new codes for warning (011,012)
3.		IBM Nandini Bheemaiah	22/08/2013  Q4 2013-RSD114-FDD5.0/TDD1.0    To Update the Payment Terms for copied PRs based on
											                                SupplierLocation or CompanyCode or Resource file

 */

package config.java.hook.sap;



import java.util.List;
import java.util.Locale;
import config.java.common.CatCommonUtil;

import ariba.approvable.core.Approvable;
import ariba.approvable.core.ApprovableHook;
import ariba.approvable.core.Comment;
import ariba.base.core.Base;
import ariba.base.core.BaseVector;
import ariba.basic.core.LocaleID;
import ariba.purchasing.core.ReqLineItem;
import ariba.purchasing.core.Requisition;
import ariba.user.core.User;
import ariba.util.core.Constants;
import ariba.util.core.FastStringBuffer;
import ariba.util.core.Fmt;
import ariba.util.core.ListUtil;
import ariba.util.core.ResourceService;
import ariba.util.core.StringUtil;
import ariba.util.log.Log;
import config.java.action.sap.CatSAPSetAdditionalChargeLineItemFields;
import config.java.common.sap.BudgetChkResp;
import config.java.common.sap.CATBudgetCheck;
import config.java.common.sap.CATSAPUtils;
//Start Q4 2013-RSD114-FDD5.0/TDD1.0
import ariba.base.core.BaseObject;
import ariba.approvable.core.LineItemCollection;
import ariba.approvable.core.Record;
import ariba.procure.core.SimpleProcureRecord;
import ariba.base.core.ClusterRoot;
import ariba.common.core.SupplierLocation;
import ariba.base.core.aql.AQLOptions;
import ariba.base.core.aql.AQLQuery;
import ariba.base.core.aql.AQLResultCollection;
import ariba.payment.core.PaymentTerms;
import ariba.base.core.BaseId;
//End Q4 2013-RSD114-FDD5.0/TDD1.0

/*   This hook performs Account Validation and Budget Check */
public class CatSAPReqSubmitHook implements ApprovableHook {



	private static final String classname = "CatSAPReqSubmitHook : ";

	private static final int ErrorCode = -1;

	private static final List NoErrorResult = ListUtil.list(Constants.getInteger(0));

	private static final String SubmitMessageInvalidField = ResourceService.getString("cat.java.sap", "SubmitMessageInvalidField");
	//"One or more Line items contains missed or invalid fields.Please complete the missing or invalid information";

	FastStringBuffer totalMsg = new FastStringBuffer ();

	boolean hasErrors = false;

	String error = "";
	//Start Q4 2013-RSD114-FDD5.0/TDD1.0
	private static final String StringTable = "aml.DefaultPartitionedPaymentTerms"; // Resource File Name

	//End Q4 2013-RSD114-FDD5.0/TDD1.0





	public List run(Approvable approvable) {

		if (!(approvable instanceof Requisition)){

			return NoErrorResult;

		}

		Requisition requisition = (Requisition) approvable;

		// Always set flag to true (used for Requisition AC defaulting from contract)
		requisition.setFieldValue("IsSubmitting",new Boolean(true));
		Log.customer.debug("%s *** IsSubmitting (entry): %s", classname, requisition.getFieldValue("IsSubmitting"));

		List lineItems = (List)requisition.getLineItems();

		// Check all the fields are valid or not before webservice call.
		if(!CATSAPUtils.isValidToSubmit(requisition))
		{
			Log.customer.debug("CatSAPReqSubmitHook : Requisition has line items with invalid/missed information");
			return ListUtil.list(Constants.getInteger(-1), SubmitMessageInvalidField);
		}

		User sessionUser = (User) Base.getSession().getEffectiveUser();
		LocaleID userLocaleID = sessionUser.getLocaleID();
		String userLanguage = userLocaleID.getLanguage();
		Locale userLocale = null;
		if (!StringUtil.nullOrEmptyOrBlankString(userLanguage)) {
			userLocale = new Locale(userLanguage);
		}
		else {
			userLocale = Locale.US;
		}

		String CatalogItemsError = Fmt.Sil(userLocale,"cat.java.sap","Error_RFQHasCatalogItems");
		if (StringUtil.nullOrEmptyOrBlankString(CatalogItemsError))
			CatalogItemsError = Fmt.Sil(Locale.US,"cat.java.sap","Error_RFQHasCatalogItems");

		String SuppSelectedError = Fmt.Sil(userLocale,"cat.java.sap","Error_RFQHasSuppSelected");
		if (StringUtil.nullOrEmptyOrBlankString(SuppSelectedError))
			SuppSelectedError = Fmt.Sil(Locale.US,"cat.java.sap","Error_RFQHasCatalogItems");

		String NeedByFlag = Fmt.Sil(userLocale,"cat.java.sap","NeedByFlag");
		if (StringUtil.nullOrEmptyOrBlankString(NeedByFlag))
			NeedByFlag = Fmt.Sil(Locale.US,"cat.java.sap","Error_RFQHasCatalogItems");

		String NeedByError = Fmt.Sil(userLocale,"cat.java.sap","NeedByLeadTimeError");
		if (StringUtil.nullOrEmptyOrBlankString(NeedByError))
			NeedByError = Fmt.Sil(Locale.US,"cat.java.sap","Error_RFQHasCatalogItems");

		String ERFQ3rdPurchasingError = Fmt.Sil(userLocale,"cat.java.sap","ERFQ3rdPurchasingError");
		if (StringUtil.nullOrEmptyOrBlankString(ERFQ3rdPurchasingError))
			ERFQ3rdPurchasingError = Fmt.Sil(Locale.US,"cat.java.sap","Error_RFQHasCatalogItems");

		String SupplierDetailsReq = Fmt.Sil(userLocale,"cat.java.sap","SupplierDetailsReq");
		if (StringUtil.nullOrEmptyOrBlankString(SupplierDetailsReq))
			SupplierDetailsReq = Fmt.Sil(Locale.US,"cat.java.sap","Error_RFQHasCatalogItems");


		boolean isPurchasing = false;
		int reqLinesSize = 0;
		User currUser = (User) Base.getSession().getEffectiveUser();
		Log.customer.debug("%s ::: Current User: %s", classname, currUser);
		if (currUser != null)
			isPurchasing = currUser.hasPermission("CatPurchasing");
		Log.customer.debug("%s ::: Current User isPurchasing: " + isPurchasing, classname);

		if (CATSAPUtils.isReqERFQ(requisition) && isPurchasing) {
			// This logic should run if a purchasing user is creating the eRFQ
			Log.customer.debug("%s ::: Setting eRFQBuyer to: %s", classname, currUser);
			requisition.setDottedFieldValue("eRFQBuyer",currUser);
		}

		if (lineItems != null) {
			reqLinesSize = lineItems.size();
		}
		if (CATSAPUtils.isReqERFQ(requisition) && isPurchasing && (reqLinesSize == 0)) {
			return ListUtil.list(Constants.getInteger(ErrorCode), ERFQ3rdPurchasingError);
		}



		// Accounting Validation
		//changed by Sandeep for MACH1 2.4

		String reqresult = CATSAPUtils.checkAccounting(requisition);
		Log.customer.debug("ResultCode is - Sandeep: " +reqresult);
		if (reqresult.equals("0") || reqresult.equals("020")) {
			hasErrors = false;
			Log.customer.debug("%s *** Result Code is 0 or 020", classname);
		}
		else
		{
			hasErrors = true;
			totalMsg.append(reqresult);
			Log.customer.debug("%s *** Req Error Msg: %s", classname, totalMsg.toString());
		}
		// Accounting Validation


		// RFQ
		if (CATSAPUtils.isReqERFQ(requisition)) {
			String errMsg = null;

			for(int i =0; i<lineItems.size();i++){
				//boolean hasBadNeedBy = false;
				boolean hasCatalogItems = false;
				boolean hasSuppSelected = false;

				ReqLineItem	rli = (ReqLineItem)lineItems.get(i);
				// Test 1 - Test if eRFQ has any Catalog Items
				if (!rli.getIsAdHoc()) {
					hasCatalogItems = true;
				}
				// Test 1 - Test if eRFQ has any items with supplier/location specified
				if (rli.getSupplier() != null || rli.getSupplierLocation() != null) {
					hasSuppSelected = true;
				}

				if (hasCatalogItems){
					hasErrors = true;
					errMsg = Fmt.S(" Line %s: ", String.valueOf(rli.getNumberInCollection())) + CatalogItemsError;
				}
				else if (hasSuppSelected) {
					hasErrors = true;
					errMsg = Fmt.S(" Line %s: ", String.valueOf(rli.getNumberInCollection())) + SuppSelectedError;
				}
				totalMsg.append(errMsg);
			}
		}
		// RFQ

		if(hasErrors){
			return ListUtil.list(Constants.getInteger(-1), totalMsg.toString());
		}

		if(CATBudgetCheck.isBudgetCheckReq(requisition)){
			BudgetChkResp budgetChkResp = null;
			CATBudgetCheck catBudgetCheck = new CATBudgetCheck();
			try {
				budgetChkResp = catBudgetCheck.performBudgetCheck(requisition);
			} catch(Exception excp){
				Log.customer.debug("CatSAPReqSubmitHook : run : excp " + excp);
				return ListUtil.list(Constants.getInteger(-1), "BudgetCheck Processing Error. Please contact System Administrator");
			}

			String BUDGET_CHECK_PASS_CODE = "000";

			//Added two new codes for warning--Code Starts --issue number 219

			//If the IO reaches less than 90 % CBS Budget check code = 000- "it allows the PR to submit"
			//Added two new codes for warning--011 - If it reaches 90% and < 100%---    011  - Warning and "it allows the PR to submit"
			//Added two new codes for warning--012 -  If it reaches 100% and <= 105 %---   012 -  Warning ( Note: At 105% also it sends 012) "it allows the PR to submit"
			//If it reaches greater than 105% error code is 001- "Internal order does not have enough Budget"  and "it does not allow the PR to submit"

			String BUDGET_CHECK_PASS_CODE1 = "011";
			String BUDGET_CHECK_PASS_CODE2 = "012";
			String BUDGET_CHECK_PASS_CODE50 = "050";  /* ADDED for CGM  PK code changes Start  */

			if (budgetChkResp == null){
				return ListUtil.list(Constants.getInteger(-1), "BudgetCheck response is null. Please contact System Administrator");
			}
			/* PK code changes Start */
			String sapSource = (String)requisition.getDottedFieldValue("CompanyCode.SAPSource");

			if (sapSource.equalsIgnoreCase("CGM")){
				if (BUDGET_CHECK_PASS_CODE50.equals(budgetChkResp.getBudgetCheckMsgCode()))
				{
					return ListUtil.list(Constants.getInteger(1), budgetChkResp.getBudgetCheckMsgTxt());
				}
			}
			/* PK code changes End */
			//Changed as per CBS BudgetCheck requirement to set it up as warning!!
			if ((BUDGET_CHECK_PASS_CODE1.equals(budgetChkResp.getBudgetCheckMsgCode())) || (BUDGET_CHECK_PASS_CODE2.equals(budgetChkResp.getBudgetCheckMsgCode())) )
			{
				return ListUtil.list(Constants.getInteger(1), budgetChkResp.getBudgetCheckMsgTxt());
			}

			if(!(BUDGET_CHECK_PASS_CODE.equals(budgetChkResp.getBudgetCheckMsgCode())))
			{
				return ListUtil.list(Constants.getInteger(-1), budgetChkResp.getBudgetCheckMsgTxt());
			}

		}

		//Added two new codes for warning--Code ends --issue number 219

		// Return warning message for Block vendor.
		String blockVendorResult = (String) CATSAPUtils.anyBlockVendor(requisition);
		if(!blockVendorResult.equals("0"))
		{
			return ListUtil.list(Constants.getInteger(1), blockVendorResult);
		}

		// No error so resync it.
		resyncAddChargeLines(lineItems);

		// must rerun checks everytime since attachment could have been deleted by user
		boolean hasAttachment = false;
		BaseVector comments = requisition.getComments();
		int cSize = comments.size();
		for (int i=0;i<cSize;i++) {
			BaseVector attchs = ((Comment)comments.get(i)).getAttachments();
			if (!attchs.isEmpty()) {
				hasAttachment = true;
				requisition.setFieldValue("AttachmentIndicator",Boolean.TRUE);
				break;
			}

		}
		if (!hasAttachment)
			requisition.setFieldValue("AttachmentIndicator",Boolean.FALSE);

		//Start Q4 2013-RSD114-FDD5.0/TDD1.0

		/**
		 * Variables used for setting the Payment Terms
		 *
		 */
		String isCopied = null;
		List records = requisition.getRecords();
		LineItemCollection lic = null;
		BaseObject companyCode = null;
		ClusterRoot paymentTermObject = null;
		PaymentTerms payTerm = null;
		SupplierLocation suploc = null;

		String reqSapSource = null;
		String queryString = "";
		String key = null;


		try
		{
			// fetch the history record of the PR
			if (records != null) {
				// get the first record, If the PR is copied, the RecordType of the first record will be CopyReqRecord
				Record rec = (Record)records.get(0);
				if (rec instanceof SimpleProcureRecord)
				{

					Log.customer.debug("%s ***Step 1 : Entering as this seem to be copied PR",classname);

					isCopied = (String) rec.getFieldValue("RecordType");
					Log.customer.debug("%s ***RecordType: %s", classname, isCopied);

					if(isCopied.equalsIgnoreCase("CopyReqRecord"))
					{
						Log.customer.debug("%s ***Copied Requisition.Set the Payment Term",classname);
						Log.customer.debug("Req Line Size" +lineItems.size());
						/*
						 *  if the PR is copied PR, go to line items and check if there is value in the
						 *  line items payment term is blank,if yes only then ,proceed to update the payment term
						 */

						for(int j =0; j<lineItems.size();j++){

							ReqLineItem	rli = (ReqLineItem)lineItems.get(j);
							Log.customer.debug("Req Line Item No" +j);
							payTerm = (PaymentTerms) rli.getFieldValue("PaymentTerms");
							if(payTerm == null)
							{
								Log.customer.debug("%s ***Payment Term is null. Set Payment Term on PR",classname);

								// To fetch the Company Code
								lic =  (LineItemCollection)rli.getLineItemCollection();
								if(lic!=null){
									Log.customer.debug("%s ***Step 2 : Get CompanyCode details",classname);
									companyCode = (BaseObject)lic.getDottedFieldValue("CompanyCode");
									Log.customer.debug("CompanyCode Object is :" +companyCode);
									Log.customer.debug("CompanyCode UniqueName:" +lic.getDottedFieldValue("CompanyCode.UniqueName"));

								}
									// To fetch the supplier location
								if (rli.getSupplierLocation() != null)
								{
									Log.customer.debug("%s ***Step 3 : Get SupplierLocation Details",classname);
									Log.customer.debug("Sup Loc Name of copied PR "+rli.getSupplierLocation().getName());

									suploc = rli.getSupplierLocation();

									Log.customer.debug("%s ***Functionality Begins",classname);
									Log.customer.debug("%s ***Step 4 : Get PaymentTerms from Supplier Location",classname);
									if(suploc.getPaymentTerms() != null)
									{
										payTerm = (PaymentTerms)suploc.getPaymentTerms();
										Log.customer.debug("%s ***Step 5 : Setting Payment Terms from Supplier Location",classname);
										Log.customer.debug("SupplierLoc PaymentTerms:"+ suploc.getPaymentTerms().getUniqueName());
									}
									else if(companyCode != null){
										Log.customer.debug("%s *** Supplier Location Payment Term is null, Go to Company Code ",classname);
										Log.customer.debug("%s ***Step 6 : Setting Payment Terms from Company Code",classname);
										payTerm = (PaymentTerms)companyCode.getFieldValue("DefaultPaymentTerms");
										Log.customer.debug("CompanyCode Default PaymentTerms:" +payTerm);
										/*
										 * If the payment Term in Supplier location and CompanyCode is null,
										 * take the payment term value from the resource file and update the copied PR.
										 */
										if(payTerm == null)
										{

											Log.customer.debug("%s ***Step 7 :CompanyCode PaymentTerms is null get the default Payment terms for SAP Source:" ,classname);
											reqSapSource = (String)companyCode.getFieldValue("SAPSource");
											Log.customer.debug("SAP Source from CompanyCode:" +reqSapSource);
											key = reqSapSource;

											Log.customer.debug("%s ***Step 8:Get KeyValue from Resource file" ,classname);
											String keyValue = ResourceService.getString(StringTable,key);
											Log.customer.debug("KeyValue from Resource file:"+keyValue);

											Log.customer.debug("%s ***Step 9 :Retrieving Payment Term based on the keyValue",classname);
											queryString ="Select this from ariba.payment.core.PaymentTerms where UniqueName = '" + keyValue + "'";
											Log.customer.debug("Query is : " + queryString);
											AQLQuery aqlQuery = AQLQuery.parseQuery(queryString);
											AQLResultCollection results = Base.getService().executeQuery(aqlQuery, baseOptions());
											if (results.getErrors() != null)
											{
												Log.customer.debug(results.getFirstError().toString());
												Log.customer.debug("No PaymentTerms for the SAP Source" + companyCode );
											}
											else
											{
												try
												{
													if (results.next())
													{
														BaseId bid = results.getBaseId(0);
														paymentTermObject = 	Base.getSession().objectFromId(bid);
														if(paymentTermObject != null)
														{
															Log.customer.debug("Payment Term object is not null");
															payTerm = (PaymentTerms) paymentTermObject;
															Log.customer.debug("Setting Default Payment term by SAPSource"+payTerm.getUniqueName());
														}

														Log.customer.debug("Payment Term from Resource file to DB  = " + paymentTermObject.getFieldValue("UniqueName"));
													}
													else
													{
														Log.customer.debug("No Results found");
													}

												}
												catch(Exception e)
												{
													Log.customer.debug(" Exception while accessing Payment Terms" );
												}
											}



										}else
										{
											Log.customer.debug("No Results found");
										}

									}

								}else
								{
									Log.customer.debug("Supplier Location is null: Payment Term cannot be set");
								}/*End if-else when supploc is null*/


								rli.setFieldValue("PaymentTerms", payTerm);
								key = null;
							}else{
								Log.customer.debug("Payment Term is already Set");
							}
						}
					}
				}
			}


		}

		catch (Exception e)
		{
			Log.customer.debug("Exception Occured : " + e);

			Log.customer.debug("Exception Details :" + ariba.util.core.SystemUtil.stackTrace(e));
		}


		//End Q4 2013-RSD114-FDD5.0/TDD1.0




		// always reset SubmitHook flag to FALSE before returning
		requisition.setFieldValue("IsSubmitting",new Boolean(false));
		Log.customer.debug("%s *** IsSubmitting (exit): %s", classname, requisition.getFieldValue("IsSubmitting"));

		return NoErrorResult;
	}


	public CatSAPReqSubmitHook() {
		super();
	}

	public static void resyncAddChargeLines (List lines) {

		int size = lines.size();
		if (size > 1) { // no need to proceed unless multiple lines

			// first determine if AC lines exist (only proceed when required)
			boolean hasAddCharges = false;
			for (int i=size-1;i>-1;i--) {
				ReqLineItem rli = (ReqLineItem)lines.get(i);
				Integer refNum = (Integer)rli.getFieldValue("ReferenceLineNumber");
				if (refNum != null && refNum.intValue() != rli.getNumberInCollection()) {
					hasAddCharges = true;
					Log.customer.debug("%s *** Found AC, must resync!", classname);
					break;
				}
			}
			if (hasAddCharges) { // only proceed if AC lines exist on Req
				for (int j=0;j<size;j++) {
					ReqLineItem rli = (ReqLineItem)lines.get(j);
					Integer refNum = (Integer)rli.getFieldValue("ReferenceLineNumber");
					int nic = rli.getNumberInCollection();
					if (refNum != null && refNum.intValue() == nic) { // material line
						for (int k=size-1;k>-1;k--){ // update associated ACs
							ReqLineItem rli2 = (ReqLineItem)lines.get(k);
							if (rli2 != rli) {
								refNum = (Integer)rli2.getFieldValue("ReferenceLineNumber");
								if (refNum != null && refNum.intValue() == nic) { //found AC line
									CatSAPSetAdditionalChargeLineItemFields.setAdditionalChargeFields(rli, rli2);
								}
							}
						}
					}
				}
			}
		}
	}

	//Start Q4 2013-RSD114-FDD5.0/TDD1.0

	public AQLOptions baseOptions() {
		Log.customer.debug("Going into baseOption method");

		AQLOptions options = new AQLOptions();
		options.setRowLimit(0);
		options.setUserLocale(Base.getSession().getLocale());
		options.setUserPartition(Base.getSession().getPartition());
		Log.customer.debug("Going into baseOption method : END");

		return options;
	}
	//End Q4 2013-RSD114-FDD5.0/TDD1.0
}



