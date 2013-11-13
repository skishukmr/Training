/* Created by KS on Oct 6, 2005 (greatly modified version of R1 order method)
 * ---------------------------------------------------------------------------------
 * Contains PO splitting and PO defaulting logic (header and line) incl. adding Comments
 * ---------------------------------------------------------------------------------
 * 12.10.05 (AK) - Added logic to track PO revisions and add related comment to PO
 * 03.11.06 (KS) - CR24 - changes for OIO Agreement (PO header field and comment)
 * 04.01.06 (Chandra) - CR26 - added comment for Contract File Number (DF&P)
 * 04.26.06 (KS) - Issue#454 - fix bug in Aggregation logic (when deleted line on PO-V2+)
 * 12.05.06
 *22.10.09 (Ashwini) - Issue 1007:Added logic to add comment on advise Price
 * 14.04.10(Ashwini) - Arkansas Issue
 * 04.13.12(Vikram)  - CR216 Modify PDW to provide all POs irrespective of
					   whether invoiced or not
 *Issue 299 - IBM_AMS_Lekshmi - Fix to update the WBSElement/IOText and AccountCategory in the header level for all Orders against the Requisition.
 *28.12.2012 (Jayashree) - Issue 293:Singapore Tc's: For Singapore PO's,
 *                    Supplier should be able to view the Terms and Conditions in ASN.
 *30.08.2013  IBM Nandini Q4 2013-RSD119-FDD5.0/TDD1.0  Implementation of OIO program functionality                    
 */

package config.java.ordering;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ariba.approvable.core.Comment;
import ariba.base.core.Base;
import ariba.base.core.BaseObject;
import ariba.base.core.BaseVector;
import ariba.base.core.ClusterRoot;
import ariba.base.core.LongString;
import ariba.base.core.MultiLingualString;
import ariba.base.core.Partition;
import ariba.base.fields.Fields;
import ariba.basic.core.Money;
import ariba.common.core.Address;
//Start Q4 2013-RSD119-FDD5.0/TDD1.0
import ariba.common.core.SupplierLocation;
//End Q4 2013-RSD119-FDD5.0/TDD1.0
import ariba.procure.core.ProcureLineItem;
import ariba.purchasing.core.Log;
import ariba.purchasing.core.POLineItem;
import ariba.purchasing.core.PurchaseOrder;
import ariba.purchasing.core.ReqLineItem;
import ariba.purchasing.core.Requisition;
import ariba.purchasing.core.ordering.OrderMethodException;
import ariba.purchasing.ordering.AllDirectOrder;
import ariba.user.core.User;
import ariba.util.core.Date;
import ariba.util.core.Fmt;
import ariba.util.core.ListUtil;
import ariba.util.core.ResourceService;
import ariba.util.core.StringUtil;
import ariba.util.formatter.BigDecimalFormatter;
import config.java.common.sap.CATSAPConstants;
import config.java.condition.sap.CatSAPAdditionalChargeLineItem;

public class CatSAPAllDirectOrder extends AllDirectOrder {
	private static final String THISCLASS = "CatSAPAllDirectOrder";
	// private static final String LEGAL_ENTITY =
	// ResourceService.getString("cat.java.vcsv1","PO_LegalEntity");
	// private static final String SHIP_TEXT =
	// ResourceService.getString("cat.java.vcsv1","PO_ShippingInstructions");
	// private static final String FOB_TEXT =
	// ResourceService.getString("cat.java.vcsv1","PO_FOBPointText");
	// private static final String FOB_TEXT_HAZMAT =
	// ResourceService.getString("cat.java.vcsv1","PO_FOBPointText_Hazmat");
	// private static final String TAX_TEXT =
	// ResourceService.getString("cat.java.vcsv1","PO_TaxInstructions");
	private static final String NTE_TEXT = ResourceService.getString(
			"cat.java.sap", "PO_NotToExceedText");

	private String shipstate = "";
	private String localIDKey = ResourceService.getString("cat.Ordering.FieldsAdded_Country_SG","SG_TnCDHLocal");
	private String companyCodeSG_Key = ResourceService.getString("cat.Ordering.FieldsAdded_Country_SG", "SG_CompanyAddRegCountry");
	private String displayKey = ResourceService.getString("cat.Ordering.FieldsAdded_Country_SG", "SG_DisplayIsY");
	//private static final String SG_COMPANY_ADDREG_COUNTRY = ResourceService.getString("cat.Ordering.FieldsAdded_Country_SG", "SG_CompanyAddRegCountry");
	//Start Q4 2013-RSD119-FDD5.0/TDD1.0 
	private static String OIOSuplr = ResourceService.getString("cat.java.sap","Req_OIOSuplrUniqueName");
	//End Q4 2013-RSD119-FDD5.0/TDD1.0
	public int canProcessLineItem(ReqLineItem lineItem)
	throws OrderMethodException {
		return 1;
	}

	public boolean canAggregateLineItems(ReqLineItem li1, POLineItem li2)
	throws OrderMethodException {
		Log.customer.debug("%s **** In canAggregateLineItems()", THISCLASS);
		int li1NIC = li1.getNumberInCollection();
		Log.customer.debug("CatSAPAllDirectOrder *** Req NIC (li1): " + li1NIC);
		//Log.customer.debug("SG_COMPANY_ADDREG_COUNTRY-- " + SG_COMPANY_ADDREG_COUNTRY);

		if (!super.sameSupplierLocation(li1, li2)) {
			Log.customer
			.debug(
					"%s **** Cannot aggregate because lines have different Supplier Locs",
					THISCLASS);
			return false;
		}
		// aggregate if line is Additional Charge and is tied to a Material Line
		// on related PO
		// 04.26.06 (KS) changed to safer way to see if ref. Material line is on
		// PO

		Integer refNum1 = (Integer) li1.getFieldValue("ReferenceLineNumber");
		Log.customer.debug("%s **** Req refNum (li1): %s ", THISCLASS, refNum1);
		if (refNum1 != null && refNum1.intValue() != li1NIC) {
			BaseVector poLines = li2.getLineItemCollection().getLineItems();
			int size = poLines.size();
			for (; size > 0; size--) {
				POLineItem poLine = (POLineItem) poLines.get(size - 1);
				int numOnReq = poLine.getNumberOnReq();
				Log.customer.debug("CatSAPAllDirectOrder **** numOnReq "
						+ numOnReq);
				if (numOnReq == refNum1.intValue()) {
					Log.customer
					.debug(
							"%s **** FORCING aggregate - AC line must be with ref material line!",
							THISCLASS);
					return true;
				}
			}
			return false;
		}
		if (!super.sameBillingAddress(li1, li2)) {
			Log.customer
			.debug(
					"%s **** Cannot aggregate because lines have different Bill Tos",
					THISCLASS);
			return false;
		}
		if (li1.getShipTo() != li2.getShipTo()) {
			Log.customer
			.debug(
					"%s **** Cannot aggregate because lines have different Ship Tos",
					THISCLASS);
			return false;
		}
		if (li1.getAmount().getCurrency() != li2.getAmount().getCurrency()) {
			Log.customer
			.debug(
					"%s **** Cannot aggregate because lines have different Currencies",
					THISCLASS);
			return false;
		}

		if (li1.getFieldValue("PaymentTerms") != li2
				.getFieldValue("PaymentTerms")) {
			Log.customer
			.debug(
					"%s **** Cannot aggregate because line items have different PayemntTerms",
					THISCLASS);
			return false;
		}

		if (li1.getFieldValue("BuyerCode") != li2.getFieldValue("BuyerCode")) {
			Log.customer
			.debug(
					"%s **** Cannot aggregate because line items have different Buyer Codes",
					THISCLASS);
			return false;
		}
		if (li1.getFieldValue("IncoTerms1") != li2.getFieldValue("IncoTerms1")) {
			Log.customer
			.debug(
					"%s **** Cannot aggregate because line items have different IncoTerms1",
					THISCLASS);
			return false;
		}

		String splitOrderOnLineType = (String) li1.getLineItemCollection()
		.getDottedFieldValue("CompanyCode.SplitOrderOnLineType");
		Log.customer.debug(
				"%s **** splitOrderOnLineType value on the companycode = "
				+ splitOrderOnLineType, THISCLASS);

		if (splitOrderOnLineType != null
				&& splitOrderOnLineType.equalsIgnoreCase("Y")) {
			if (li1.getFieldValue("LineItemType") != li2
					.getFieldValue("LineItemType")) {
				Log.customer
				.debug(
						"%s **** Cannot aggregate because line items have different LineItemType",
						THISCLASS);
				return false;
			}
		}

		// IsHazmat has possibility of nulls on pre-R4 requisitions (treat null
		// as FALSE)
		Boolean haz1 = (Boolean) li1.getFieldValue("IsHazmat");
		Boolean haz2 = (Boolean) li2.getFieldValue("IsHazmat");
		Log.customer.debug("%s **** hazmat1 / hazmat2 (Before): %s / %s",
				THISCLASS, haz1, haz2);
		haz1 = haz1 == null ? Boolean.FALSE : haz1;
		haz2 = haz2 == null ? Boolean.FALSE : haz2;
		Log.customer.debug("%s **** hazmat1 / hazmat2 (After): %s / %s",
				THISCLASS, haz1, haz2);
		if (!haz1.equals(haz2)) {
			Log.customer
			.debug(
					"%s **** Cannot aggregate because lines have different IsHazmat",
					THISCLASS);
			return false;
		}
		if (!super.samePunchOut(li1, li2)) {
			Log.customer
			.debug(
					"%s **** Cannot aggregate because lines from different PunchOut sites",
					THISCLASS);
			return false;
		}
		if (!super.sameMasterAgreement(li1, li2)) {
			Log.customer
			.debug(
					"%s **** Cannot aggregate because lines from different Contracts",
					THISCLASS);
			return false;
		}
		if (super.isChangeOrder(li1)) {
			int i = changeOrderRestriction(li1, li2);
			if (i == -1) {
				Log.customer
				.debug(
						"%s **** Cannot aggregate because of Super's change order restriction",
						THISCLASS);
				return false;
			}
		}
		return true;
	}

	public List endProcessingRequisition(Requisition req)
	throws OrderMethodException {
		Log.customer.debug("%s **** endProcessingRequisition!", THISCLASS);
		Log.customer.debug("%s **** Requisition: %s", THISCLASS, req);
		// Boolean emergencyBuy = (Boolean)req.getFieldValue("EmergencyBuy");
		// Log.customer.debug("%s **** REQ Emergency Buy: %s",THISCLASS,emergencyBuy);
		BaseVector lines = req.getLineItems();
		int lineCount = lines.size();
		Log.customer.debug("CatSAPAllDirectOrder **** Req lines size: "
				+ lineCount);
		ArrayList polist = new ArrayList();
		ArrayList newIDpoList = new ArrayList();
		ArrayList referToOldIDpoList = new ArrayList();

		// Added by James
		String acctCategory = (String) req
		.getDottedFieldValue(CATSAPConstants.ACCNT_CATGORY_FIELD_NAME);

		if (acctCategory != null) {
			// Issue 299 : Fix to update the WBSElement/IOText and
			// AccountCategory in the header level for all Orders against the
			// Requistion.

			for (int i = 0; i < lineCount; i++) {

				PurchaseOrder po = ((ReqLineItem) lines.get(i)).getOrder();
				Log.customer.debug("%s **** Lineitem's  order is %s",
						THISCLASS, po);
				if (po != null) {
					Log.customer.debug("%s **** Lineitem's  order's UniqueName  is %s",
							THISCLASS, po.getUniqueName());
					// Issue 299 End
					String accountCategory = (String) po
					.getDottedFieldValue(CATSAPConstants.ACCNT_CATGORY_FIELD_NAME);
					Log.customer.debug(
							"%s **** Getting ORDER Account Category: %s",
							THISCLASS, accountCategory);
					String wbsElement = (String) po
					.getDottedFieldValue(CATSAPConstants.WBS_ELEMENT_FIELD_NAME);
					Log.customer.debug("%s **** Getting ORDER wbsElement %s",
							THISCLASS, wbsElement);
					String ioElement = (String) po
					.getDottedFieldValue(CATSAPConstants.IO_ELEMENT_FIELD_NAME);
					Log.customer.debug("%s **** Getting ORDER ioElement: %s",
							THISCLASS, ioElement);
					if (StringUtil.nullOrEmptyOrBlankString(accountCategory))
						po.setDottedFieldValue(
								CATSAPConstants.ACCNT_CATGORY_FIELD_NAME,
								acctCategory);

					if (CATSAPConstants.WBS_ACCT_CATEGORY.equals(acctCategory)) {
						if (StringUtil.nullOrEmptyOrBlankString(wbsElement)) {
							po
							.setDottedFieldValue(
									CATSAPConstants.WBS_ELEMENT_FIELD_NAME,
									req
									.getDottedFieldValue(CATSAPConstants.WBS_ELEMENT_FIELD_NAME));
						}

					} else if (CATSAPConstants.IO_ACCT_CATEGORY
							.equals(acctCategory)) {
						if (StringUtil.nullOrEmptyOrBlankString(ioElement)) {
							po
							.setDottedFieldValue(
									CATSAPConstants.IO_ELEMENT_FIELD_NAME,
									req
									.getDottedFieldValue(CATSAPConstants.IO_ELEMENT_FIELD_NAME));
						}

					}
					// Issue 299 : Fix to update the WBSElement/IOText and
					// AccountCategory in the header level for all Orders
					// against
					// the Requistion.
				}
			}
			// Issue 299 End}
		}
		for (int i = 0; i < lineCount; i++) {
			ReqLineItem rli = (ReqLineItem) req.getLineItems().get(i);
			Log.customer.debug("%s *** ReqLineItem: %s ", THISCLASS, rli);
			PurchaseOrder po = rli.getOrder();
			String poUniqueName = (String) po.getFieldValue("OrderID");
			if (po != null) {
				// add PO to List if not present
				ListUtil.addElementIfAbsent(polist, po);
				Log.customer.debug("%s **** ADDING ORDER#: %s", THISCLASS, po
						.getOrderID());
				// set PO Hazmat if applicable (any rli has IsHazmat = TRUE);
				Boolean isLineHazmat = (Boolean) rli.getFieldValue("IsHazmat");
				Log.customer.debug("%s *** isLineHazmat: %s ", THISCLASS,
						isLineHazmat);
				if (isLineHazmat != null && isLineHazmat.booleanValue()) {
					Log.customer.debug(
							"%s *** Hazmat so setting header fields!",
							THISCLASS);
					po.setFieldValue("IsHazmat", isLineHazmat);
					// po.setFieldValue("FOBPoint",FOB_TEXT_HAZMAT);
				}

				// AEK set the Related PO field on the req line item for
				// comments on change orders
				// Commented because it is not in SAP partition-garima
				/*
				 * String existingRelatedPO =
				 * (String)rli.getFieldValue("RelatedPO");
				 * if(!StringUtil.nullOrEmptyOrBlankString(existingRelatedPO)){
				 * Log.customer.debug(
				 * "%s *** Comparing values for rli and po, which is: %s "
				 * ,THISCLASS, poUniqueName);
				 * if(!existingRelatedPO.equalsIgnoreCase(poUniqueName)){
				 * Log.customer.debug(
				 * "%s *** There is a new PO for this rli.  look for comment added."
				 * ,THISCLASS); //since the po number has changed on this rli,
				 * add this po to a special list of changed po's
				 * if(!newIDpoList.contains(poUniqueName)){
				 * newIDpoList.add(poUniqueName);
				 * Log.customer.debug("%s *** Add to newIDpoList po#: "
				 * ,poUniqueName); //then immediately after that, add the name
				 * of the po that this is taking the place of
				 * referToOldIDpoList.add(existingRelatedPO);
				 * Log.customer.debug(
				 * "%s *** Add to referToOldIDpoList po#: ",existingRelatedPO);
				 * } //now that we've captured the old po id, we have to update
				 * the related po field on the rli
				 * rli.setFieldValue("RelatedPO",poUniqueName); } } else {
				 * Log.customer
				 * .debug("%s *** Setting rli's related PO: %s ",THISCLASS,
				 * poUniqueName); rli.setFieldValue("RelatedPO",poUniqueName); }
				 */
			}
		}
		int listsize = polist.size();
		Log.customer.debug("CatSAPAllDirectOrder *** polist size: " + listsize);
		// Added by Majid **** Starts here
		for (int i = 0; i < listsize; i++) {
			boolean hasNotToExceed = false;
			boolean hastaxdetails = false;
			//Start Q4 2013-RSD119-FDD5.0/TDD1.0
			boolean isOIOAgreement = false;
			//End Q4 2013-RSD119-FDD5.0/TDD1.0
			PurchaseOrder po = (PurchaseOrder) polist.get(i);
			String newPOUniqueName = po.getUniqueName();
			Log.customer.debug("CatSAPAllDirectOrder PurchaseOrder  =>" + po);
			Log.customer
			.debug("CatSAPAllDirectOrder PurchaseOrder UniqueName =>"
					+ newPOUniqueName);
			Date date = Date.getNow();
			Log.customer.debug("CatSAPAllDirectOrder date =>" + date);
			String closeorderafter = Base.getService().getParameter(null,
			"System.Base.CloseOrderAfter");
			Log.customer.debug("CatSAPAllDirectOrder closeorderafter =>"
					+ closeorderafter);
			int idays = -1;
			if (closeorderafter != null)
				idays = Integer.parseInt(closeorderafter);
			Log.customer
			.debug("CatSAPAllDirectOrder After parsing the param value for idays =>"
					+ idays);
			if (idays != -1) {
				Log.customer
				.debug("CatSAPAllDirectOrder date before adding idays =>"
						+ date);
				Date.addDays(date, idays);
				Log.customer.debug("CatSAPAllDirectOrder date =>" + date);
				po.setFieldValue("CloseOrderDate", date);
			}

			// set DWPOFlag and Topic Name (CR216)
			po.setFieldValue("DWPOFlag", "InProcess");
			po.setFieldValue("TopicName", "DWPOPush");


			




			// Added by Majid to set Buyer Code and Buyer Contact field at
			// header level *** starts here
			lineCount = po.getLineItemsCount();
			Log.customer.debug("CatSAPAllDirectOrder linecount =>" + lineCount);
			if (lineCount > 0) {
				BaseVector poLines = po.getLineItems();
				POLineItem pli1 = (POLineItem) poLines.get(0);
				ClusterRoot buyerCode = (ClusterRoot) pli1
				.getFieldValue("BuyerCode");
				Log.customer.debug("CatSAPAllDirectOrder buyerCode =>"
						+ buyerCode);
				// Arkansa start

				ClusterRoot compCode1 = (ClusterRoot) po
				.getDottedFieldValue("CompanyCode");
				Log.customer.debug("%s *** COMPCODE!!!111, price: %s",
						THISCLASS, compCode1);
				String ccde = (String) compCode1.getFieldValue("UniqueName");
				Log.customer.debug("%s *** COMPANY CODEEEEE!!!111, price: %s",
						THISCLASS, ccde);
				String tCode = (String) pli1
				.getDottedFieldValue("TaxCode.UniqueName");
				Log.customer.debug("%s *** TCODE!!!111, price: %s", THISCLASS,
						tCode);
				Address shiptoar = (Address) pli1.getDottedFieldValue("ShipTo");
				shipstate = shiptoar.getState();
				Log.customer.debug(" %s : ****** shipstate %s ", THISCLASS,
						shipstate);
				String cc = "1000";
				String tc = "B3";
				if (ccde.equals("1000") && shipstate.equalsIgnoreCase("AR")) {
					hastaxdetails = true;
					Log.customer.debug("%s HAS TAX = TRUE", THISCLASS);
				}

				// Arkansas end
				if (buyerCode != null) {
					po.setFieldValue("BuyerCode", buyerCode);
					StringBuffer contact = null;
					User user = (User) buyerCode.getFieldValue("UserID");
					Log.customer
					.debug("CatSAPAllDirectOrder buyerCode's user =>"
							+ user);
					if (user != null) {
						MultiLingualString name = user.getName();
						contact = name == null ? new StringBuffer("Buyer")
						: new StringBuffer(name.getPrimaryString());
						ariba.common.core.User pUser = ariba.common.core.User
						.getPartitionedUser(user, po.getPartition());
						Log.customer
						.debug("CatSAPAllDirectOrder buyerCode's partition user object=>"
								+ pUser);
						if (pUser != null) {
							String phone = (String) pUser
							.getFieldValue("DeliverToPhone");
							Log.customer
							.debug("CatSAPAllDirectOrder buyerCode's DeliverToPhone=>"
									+ phone);
							if (phone != null)
								contact.append(", ").append(phone);
						}
					} else {
						contact = new StringBuffer(buyerCode.getUniqueName());
					}
					po.setFieldValue("BuyerContact", contact.toString());
				}

				// Set IncoTerm1 and IncoTerm2 from First Line Item to Header
				// Level... *** Starts here
				ClusterRoot incoTerms1CR = (ClusterRoot) pli1
				.getFieldValue("IncoTerms1");
				String incoTermsFinal = null;
				if (incoTerms1CR != null) {
					String incoTerms1Str = (String) incoTerms1CR
					.getDottedFieldValue("UniqueName");
					String incoTerms2Str = (String) pli1
					.getFieldValue("IncoTerms2");
					if (incoTerms2Str != null)
						incoTermsFinal = incoTerms1Str + " - " + incoTerms2Str;
					else
						incoTermsFinal = incoTerms1Str;

				}
				Log.customer.debug("CatSAPAllDirectOrder incoTermsFinal =>"
						+ incoTermsFinal);
				if (incoTermsFinal != null) {
					po.setFieldValue("IncoTerms", incoTermsFinal);
					Log.customer
					.debug("CatSAPAllDirectOrder set the value of IncoTerms with  =>"
							+ incoTermsFinal);
				}
				// Set IncoTerm1 and IncoTerm2 from First Line Item to Header
				// Level... *** Starts here

				// Added by Majid to set Buyer Code and Buyer Contact field at
				// header level *** starts here

				// Santanu : update ReferenceNumber on PO line (0th element will
				// be material - ref. itself)
				// check for "Price Not to Exceed" condition
				String reasonCode = (String) pli1
				.getDottedFieldValue("Description.ReasonCode");
				Money price = pli1.getDescription().getPrice();
				Log.customer.debug("%s *** reasonCode, price: %s, %s",
						THISCLASS, reasonCode, price);
				BigDecimal zero = new BigDecimal(0.00);
				if (price.getAmount().compareTo(zero) == 0
						&& reasonCode != null
						&& reasonCode.indexOf("xceed") > -1) {
					hasNotToExceed = true;
					// setReasonCode(pli1, reasonCode);
					pli1
					.setDottedFieldValue("Description.ReasonCode",
							NTE_TEXT);
				}

				pli1.setFieldValue("ReferenceLineNumber", new Integer(pli1
						.getNumberInCollection()));
				Log.customer.debug("%s *** Set Material RefNum (Line#1)",
						THISCLASS);

				// Santanu : Modified for the additional charge requirements
				// for remaining lines, set DeliverTo && handle "Not to Exceed"
				// condition

				if (lineCount > 1) {
					for (int j = 1; j < lineCount; j++) {
						POLineItem poli = (POLineItem) poLines.get(j);
						// set DeliverTo (to leverage Ariba HTMLFormatter to
						// display at line vs. header)
						// Log.customer.debug("%s *** DELIVERTO (BEFORE): %s ",THISCLASS,
						// poli.getDeliverTo());
						poli.setFieldValue("DeliverTo", poli.getDeliverTo());
						// Log.customer.debug("%s *** DELIVERTO (AFTER): %s ",THISCLASS,
						// poli.getDeliverTo());
						// check for "Price Not to Exceed" condition
						price = poli.getDescription().getPrice();
						reasonCode = (String) poli
						.getDottedFieldValue("Description.ReasonCode");
						Log.customer.debug("%s *** reasonCode, price: %s, %s",
								THISCLASS, reasonCode, price);
						if (price.getAmount().compareTo(zero) == 0
								&& reasonCode != null
								&& reasonCode.indexOf("xceed") > -1) {
							hasNotToExceed = true;
							// setReasonCode(poli, reasonCode);
							poli.setDottedFieldValue("Description.ReasonCode",
									NTE_TEXT);
						}

						// Santanu : update ReferenceNumber on PO lines
						if (!CatSAPAdditionalChargeLineItem
								.isAdditionalCharge(poli)) {
							poli.setFieldValue("ReferenceLineNumber",
									new Integer(poli.getNumberInCollection()));
							Log.customer
							.debug("CatSAPAllDirectOrder *** Set Material RefNum to NIC!");
						} else { // must be an additional charge (update
							// position based on req order splitting)
							int numOnReq = poli.getNumberOnReq();
							ReqLineItem reqline = (ReqLineItem) req
							.getLineItem(numOnReq);
							if (reqline != null) {
								Integer reqRefNum = (Integer) reqline
								.getFieldValue("ReferenceLineNumber");
								Log.customer.debug(
										"%s *** AC refNum (to find): %s ",
										THISCLASS, reqRefNum);
								if (reqRefNum != null) {
									int refNum = reqRefNum.intValue();
									for (int k = 0; k < lineCount; k++) {
										POLineItem li = (POLineItem) poLines
										.get(k);
										numOnReq = li.getNumberOnReq();
										Log.customer
										.debug("CatSAPAllDirectOrder *** AC refNum: "
												+ numOnReq);
										if (numOnReq == refNum) {
											poli
											.setFieldValue(
													"ReferenceLineNumber",
													new Integer(
															li
															.getNumberInCollection()));
											// Log.customer.debug("CatSAPAllDirectOrder *** Set AC RefNum to:"
											// + li.getNumberInCollection());
											break;
										}
									}
								}
							}
						}
						// appendQuoteReference(poli);
					}
				}

				// Santanu : Modified for the additional charge requirement

			}

			// Added by majid to add Tax instructon on PO Print *** starts here

			// Get resource key by passing Company Code and Field to get
			String targetFieldName = "DisplayTaxInst";
			String taxTextResourceKey = null;
			taxTextResourceKey = getCompanyBasedPOResourceKey(po,
					targetFieldName);
			if (taxTextResourceKey != null) {
				String TAX_TEXT = ResourceService.getString("cat.poprint.sap",
						taxTextResourceKey);
				po.setFieldValue("TaxInstructions", TAX_TEXT);
			}
			
			//Start Q4 2013-RSD119-FDD5.0/TDD1.0
			// RSD 119 - Set PO header OIOAgreement when applicable
			try {
				
				SupplierLocation sloc = po.getSupplierLocation();
				String supplierUniqueName = sloc.getSupplier().getUniqueName();

				if (sloc != null && OIOSuplr.equals(supplierUniqueName)) {
					Boolean oio = (Boolean)req.getFieldValue("OIOAgreement");
					Log.customer.debug("CatCSVAllDirectOrder **** OIOAgreement: " + oio);
					if (oio != null && oio.booleanValue()) {
						isOIOAgreement = true;
						po.setFieldValue("OIOAgreement",oio);
					}
				}

				
			} catch (Exception e)
			{
				Log.customer.debug("Exception Occured : " + e);

				Log.customer.debug("Exception Details :" + ariba.util.core.SystemUtil.stackTrace(e));
			}    	    	
			//End Q4 2013-RSD119-FDD5.0/TDD1.0 
			
			
			

			// ADD Applicable Comments
			Partition partition = po.getPartition();
			User admin = po.getRequester();
			Comment comment = null;
			String commentText = null;
			String commentTitle = null;

			int poVersion = po.getVersionNumber().intValue();

			boolean hasHAZComment = false;
			boolean hasNTEComment = false;
			boolean hasTERMSComment = false;
			boolean hasNOTESComment = false;
			//Start Q4 2013-RSD119-FDD5.0/TDD1.0
			boolean hasOIOComment = false;
			//End Q4 2013-RSD119-FDD5.0/TDD1.0
			boolean hasContractFileNum = false;
			boolean hasShippInst = false;
			boolean hasTaxInfo = false;

			// 0. Before creating new comments, check if already exist (on
			// change orders)
			if (poVersion > 1) {
				BaseVector comments = po.getComments();
				if (comments != null && !comments.isEmpty()) {
					int size = comments.size();
					for (; size > 0; size--) {
						Comment oldComment = (Comment) comments.get(size - 1);
						String title = oldComment.getTitle();
						// 04.26.06 (KS) Terms Comment not copying over from V1
						if (title.indexOf("TERMS") > -1)
							hasTERMSComment = true;
						else if (title.indexOf("ADVISE") > -1)
							hasNTEComment = true;
						else if (title.indexOf("HAZ") > -1)
							hasHAZComment = true;
						else if (title.indexOf("NOTES") > -1)
							hasNOTESComment = true;
						else if (title.indexOf("OIO") > -1)
							hasOIOComment = true;
						else if (title.indexOf("CONTRACT") > -1)
							hasContractFileNum = true;
						else if (title.indexOf("SHIPPING") > -1)
							hasShippInst = true;
						else if (title.indexOf("INFORMAT") > -1)
							hasTaxInfo = true;
					}
				}
			}
			//Start Q4 2013-RSD119-FDD5.0/TDD1.0
			Log.customer.debug("CatCSVAllDirectOrder *** hasNTEComment/hasHAZComment/hasTERMSComment/hasOIOComment: "
					+ hasNTEComment + hasHAZComment + hasTERMSComment + hasOIOComment);


			// RSD 119 If applicable, add "OIO Agreement" notice
			try {
				if(isOIOAgreement && !hasOIOComment) {
					commentText = ResourceService.getString("cat.java.sap","PO_OIOAgreement");
					if (commentText != null) {
						comment = new Comment(partition);
						if (comment != null) {
							comment.setParent(po);
							comment.setUser(admin);
							commentTitle = ResourceService.getString("cat.java.sap","PO_OIOTitle");

							setCommentDetails(comment,1,true,commentText,commentTitle,commentTitle);
							po.getComments().add(comment);
							Log.customer.debug("%s *** Added Comment (OIO Agreement)!",THISCLASS);
						}
					}
				}
			} catch (Exception e)
			{
				Log.customer.debug("Exception Occured : " + e);

				Log.customer.debug("Exception Details :" + ariba.util.core.SystemUtil.stackTrace(e));
			} 
			
			//End Q4 2013-RSD119-FDD5.0/TDD1.0
			
			
			
			// Added code for comment on MACH1 POs Issue 1007 start
			if (hasNotToExceed && !hasNTEComment) {
				String sub = "\"advise price\"";
				commentText = ResourceService.getString("cat.java.sap",
				"PO_CostNotToExceed");
				commentText = Fmt.S(commentText, sub);
				if (commentText != null) {
					comment = new Comment(partition);
					if (comment != null) {
						comment.setParent(po);
						comment.setUser(admin);
						commentTitle = "ADVISE PRICE";
						setCommentDetails(comment, 1, true, commentText,
								commentTitle, commentTitle);
						po.getComments().add(comment);
						Log.customer.debug(
								"%s *** Added Comment (COST NOT TO EXCEED)!",
								THISCLASS);
					}
				}
			}
			// Added code for comment on MACH1 POs Issue 1007 ends
			// Arkansas Issue start
			if (hastaxdetails) {
				Log.customer.debug("%s *** ENTERED HASTAXDETAILS LOOOOOOP!",
						THISCLASS);
				String sub = "\"tax details\"";
				commentText = ResourceService.getString("cat.java.sap",
				"TaxArkansas");
				commentText = Fmt.S(commentText, sub);
				if (commentText != null) {
					comment = new Comment(partition);
					if (comment != null) {
						comment.setParent(po);
						comment.setUser(admin);
						commentTitle = "TAX DETAILS";
						setCommentDetails(comment, 1, true, commentText,
								commentTitle, commentTitle);
						po.getComments().add(comment);
						Log.customer
						.debug("%s *** Added Comment (TAXDETAILS)!",
								THISCLASS);
					}
				}

			}

			// 3. Always add standard Notes (multiple notes handled via
			// formatting)
			if (!hasNOTESComment) {
				targetFieldName = "DisplayStandNotes";
				String dispStdNotesResourceKey = null;
				dispStdNotesResourceKey = getCompanyBasedPOResourceKey(po,
						targetFieldName);
				if (dispStdNotesResourceKey != null) {
					commentText = ResourceService.getString("cat.poprint.sap",
							dispStdNotesResourceKey);
					if (commentText != null) {
						comment = new Comment(partition);
						if (comment != null) {
							comment.setParent(po);
							comment.setUser(admin);
							commentTitle = "NOTES";
							setCommentDetails(comment, 1, true, commentText,
									commentTitle, commentTitle);
							po.getComments().add(comment);
						}
					}
				}
			}

			// 5. Always add Terms & Conditions text (ONLY on PO Version 1, not
			// V2+ since already exists)

			targetFieldName = "DisplayTerms";
			String dispTermsResourceKey = null;
			dispTermsResourceKey = getCompanyBasedPOResourceKey(po,
					targetFieldName);
			if (dispTermsResourceKey != null) {
				commentText = ResourceService.getString("cat.poprint.sap",
						dispTermsResourceKey);
				if (commentText != null && !hasTERMSComment) {
					comment = new Comment(partition);
					if (comment != null) {
						comment.setParent(po);
						comment.setUser(admin);
						commentTitle = "TERMS and CONDITIONS";
						setCommentDetails(comment, 1, true, commentText,
								commentTitle, commentTitle);
						po.getComments().add(comment);
						Log.customer.debug(
								"%s *** Added Comment (TERMS&CONDITIONS)!",
								THISCLASS);
					}
				}
			}

			// 6. Add Shipping Instruction (ONLY on PO Version 1, not V2+ since
			// already exists)
			if (!hasShippInst) {
				targetFieldName = "DisplayShippInst";
				String dispShipInstResourceKey = null;
				dispShipInstResourceKey = getCompanyBasedPOResourceKey(po,
						targetFieldName);
				if (dispShipInstResourceKey != null) {
					commentText = ResourceService.getString("cat.poprint.sap",
							dispShipInstResourceKey);
					if (commentText != null) {
						comment = new Comment(partition);
						if (comment != null) {
							comment.setParent(po);
							comment.setUser(admin);
							commentTitle = "SHIPPING INSTRUCTIONS";
							setCommentDetails(comment, 1, true, commentText,
									commentTitle, commentTitle);
							po.getComments().add(comment);
						}
					}
				}
			}

			// 6. Add Shipping Instruction (ONLY on PO Version 1, not V2+ since
			// already exists)
			if (!hasShippInst) {
				targetFieldName = "DisplayTaxInfoComm";
				String dispShipInstResourceKey = null;
				dispShipInstResourceKey = getCompanyBasedPOResourceKey(po,
						targetFieldName);
				if (dispShipInstResourceKey != null) {
					commentText = ResourceService.getString("cat.poprint.sap",
							dispShipInstResourceKey);
					if (commentText != null) {
						comment = new Comment(partition);
						if (comment != null) {
							comment.setParent(po);
							comment.setUser(admin);
							commentTitle = "TAX INFORMATION";
							setCommentDetails(comment, 1, true, commentText,
									commentTitle, commentTitle);
							po.getComments().add(comment);
						}
					}
				}
			}

			/** Starts: Issue 293 -
			 *          Purpose: 1)To add the Terms and Conditions under comment section for Singapore PO's.
			 *		             2)Always add Terms & Conditions text As(ONLY on PO Version 1, not
			 *                     V2+ since already exists)
			 */

			String companyRegAddCountry = null;
			if (po.getFieldValue("CompanyCode") != null){
				Log.customer.debug("CatSAPAllDirectOrder CompanyCode is not null");
				if((po.getDottedFieldValue("CompanyCode.RegisteredAddress") != null)){
					if((po.getDottedFieldValue("CompanyCode.RegisteredAddress.Country") != null)){
						companyRegAddCountry = (String) po.getDottedFieldValue("CompanyCode.RegisteredAddress.Country.UniqueName");
						Log.customer.debug("CatSAPAllDirectOrder field companyRegAddCountry is not null"+companyRegAddCountry);
					}
				}
			}

			if(companyRegAddCountry != null && companyRegAddCountry.equalsIgnoreCase(companyCodeSG_Key)){
				Log.customer.debug("%s Inside If companyCodeSG_Key = SG"+companyCodeSG_Key);
				targetFieldName = ResourceService.getString("cat.Ordering.FieldsAdded_Country_SG","SG_DisplayTC");
				String dispTermsResourceKeySG = null;
				dispTermsResourceKeySG = getCompanyBasedPOResourceKey(po,targetFieldName);
				Log.customer.debug("%s Resourcekey returned is"+dispTermsResourceKeySG);
				if (dispTermsResourceKeySG  != null) {
					commentText = ResourceService.getString("resource.ordering",
							dispTermsResourceKeySG);
					if (commentText != null && !hasTERMSComment) {
						comment = new Comment(partition);
						if (comment != null) {
							comment.setParent(po);
							comment.setUser(admin);
							commentTitle = ResourceService.getString("cat.Ordering.FieldsAdded_Country_SG","SG_CommentTitle");
							setCommentDetails(comment, 1, true, commentText,
									commentTitle, commentTitle);
							po.getComments().add(comment);
							Log.customer.debug(
									"%s *** Added Comment (TERMS&CONDITIONS)!",
									THISCLASS);
						}
					}
				}
			}

			Log.customer.debug("%s Either field Name companyRegAddCountry or resource key companyCodeSG_Key is null."+THISCLASS);
			//Issue 293 Ends:

			// Added by majid to add Tax instructon on PO Print *** Ends here
		}
		// Added by Majid **** Ends here
		return super.endProcessingRequisition(req);
	}

	private static void setReasonCode(ProcureLineItem pli, String reasoncode) {

		if (pli != null && reasoncode != null) {
			Money ntePrice = (Money) pli
			.getDottedFieldValue("Description.NotToExceedPrice");
			if (ntePrice != null) {
				// first check to see if $ value already exists (may if
				// revision)
				int index = reasoncode.indexOf("$");
				if (index > -1)
					reasoncode = reasoncode.substring(0, index); // change
				// order,
				// remove
				// earlier $
				// value
				StringBuffer rc = new StringBuffer(reasoncode);
				BigDecimal nteAmount = BigDecimalFormatter.round(ntePrice
						.getAmount(), 2);
				rc.append(" $").append(nteAmount.toString());
				Log.customer.debug("%s *** reasonCode SB: %s", THISCLASS, rc);
				rc.append(ntePrice.getCurrency().getUniqueName());
				Log.customer.debug("%s *** reasonCode SB: %s", THISCLASS, rc);
				pli
				.setDottedFieldValue("Description.ReasonCode", rc
						.toString());
			}
		}
	}

	private static void setCommentDetails(Comment comment, int type,
			boolean isExternal, String message, String title, String name) {

		if (comment != null) {
			comment.setDate(Fields.getService().getNow());
			comment.setType(type);
			comment.setExternalComment(isExternal);
			if (!StringUtil.nullOrEmptyOrBlankString(message))
				comment.setText(new LongString(message));
			if (!StringUtil.nullOrEmptyOrBlankString(title))
				comment.setTitle(title);
			if (!StringUtil.nullOrEmptyOrBlankString(name))
				comment.setCommentName(name);
		}
	}

	/*
	 * private static StringBuffer formatDeliverTo (ProcureLineItem pli) {
	 *
	 * StringBuffer sb = new StringBuffer(); if (pli != null) { String deliverto
	 * = pli.getDeliverTo(); if (deliverto != null) {
	 * sb.append(pli.getDeliverTo()); String mailstop =
	 * (String)pli.getFieldValue("DeliverToMailStop"); if (mailstop != null &&
	 * deliverto.indexOf(";") < 0) { sb.append("; "); sb.append(mailstop); } } }
	 * return sb; }
	 */

	/*
	 * private static void appendQuoteReference (ProcureLineItem pli){
	 *
	 * StringBuffer desc = new
	 * StringBuffer(pli.getDescription().getDescription()); String quote =
	 * (String)pli.getFieldValue("SupplierQuoteReference"); if (desc != null &&
	 * !StringUtil.nullOrEmptyOrBlankString(quote)) {
	 * desc.append(" (").append(QuoteRef_TEXT).append(quote).append(")");
	 * pli.setDottedFieldValue("Description.Description",desc.toString()); } }
	 */

	public String getCompanyBasedPOResourceKey(PurchaseOrder po,
			String targetFieldName) {
		String resourceKey = null;
		Log.customer.debug("CatSAPAllDirectOrder targetFieldName =>"
				+ targetFieldName);
		Log.customer.debug("CatSAPAllDirectOrder po =>" + po.getUniqueName());
		ClusterRoot compCode = (ClusterRoot) po
		.getDottedFieldValue("CompanyCode");
		if (compCode == null) {
			Log.customer.debug("CatSAPAllDirectOrder CompanyCode is null =>"
					+ po.getDottedFieldValue("CompanyCode"));
			return resourceKey;
		}

		Log.customer.debug("CatSAPAllDirectOrder CompanyCode =>"
				+ po.getDottedFieldValue("CompanyCode"));
		List dispoprint = (List) po
		.getDottedFieldValue("CompanyCode.DisplayPOPrintFields");
		Log.customer.debug("CatSAPAllDirectOrder dispoprint =>" + dispoprint);
		BaseObject dispo;
		if (dispoprint != null) {
			for (Iterator it = dispoprint.iterator(); it.hasNext();) {
				dispo = (BaseObject) it.next();
				String fieldName = (String) dispo
				.getDottedFieldValue("FieldName");
				String display = (String) dispo.getDottedFieldValue("Display");
				String localID = (String)dispo.getDottedFieldValue("LocalID");
				String importID = (String)dispo.getDottedFieldValue("ImportID");

				if (targetFieldName.equalsIgnoreCase(fieldName)) {
					if (display.equalsIgnoreCase("Y")) {
						localID = (String) dispo.getDottedFieldValue("LocalID");
						if (localID != null)
							resourceKey = localID;
					}
				}


				/** Starts: Issue 293:
				 *          Purpose: 1)Below Code, checks null condition for CompanyCode,RegisteredAddress,Country.
				 *          2)And then it assigns the resourceKey to TnCDHLocal, when CompanyCode's registered country is SG.
				 *
				 */
				String companyRegAddCountry = null;
				if (po.getFieldValue("CompanyCode") != null){
					Log.customer.debug("CatSAPAllDirectOrder CompanyCode is not null");
					if((po.getDottedFieldValue("CompanyCode.RegisteredAddress") != null)){
						if((po.getDottedFieldValue("CompanyCode.RegisteredAddress.Country") != null)){
							companyRegAddCountry = (String) po.getDottedFieldValue("CompanyCode.RegisteredAddress.Country.UniqueName");
							Log.customer.debug("CatSAPAllDirectOrder field companyRegAddCountry is not null"+companyRegAddCountry);
						}
					}
				}
				if(companyRegAddCountry != null){
					if(companyRegAddCountry.equalsIgnoreCase(companyCodeSG_Key) && localID != null){
						Log.customer.debug(" CatSAPAllDirectOrder: Enters If when Company Code's address Country is SG and localId"+localID);
						if(localID.equalsIgnoreCase(localIDKey)){
							if (targetFieldName.equalsIgnoreCase(fieldName)) {
								Log.customer.debug("CatSAPAllDirectOrder: Enters If when TargetFieldName is displayTermsConditions");
								if (display.equalsIgnoreCase(displayKey)) {
									localID = (String) dispo.getDottedFieldValue("LocalID");
									if (localID != null)
										resourceKey = localID;
								}
							}
						}
					}
				}

				// Ends: Issue 293
			}
		}
		Log.customer.debug("CatSAPAllDirectOrder : resourceKey =>"
				+ resourceKey);
		return resourceKey;

	}

	public CatSAPAllDirectOrder() {
	}

}
