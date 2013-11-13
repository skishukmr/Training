/*********************************************************************************
Ashwini.M :   		20/08/08
Issue  :			955
Description:  		Added ReferenceLineNumber in InvoiceReconciliation
23/09/2011 Code added for Vertex changes
 *************************************************************************************/
 /*********************************************************************************
Rahul Ranjan :   		25/10/2011
Issue        :			218
Description  :  		Added Condition to make InternalOrderText, CostCenterText and WBSElementText
						as null depending on AccountCategory.
Aswini		   :		26/10/2011
Change         :		Code changes for Vertex.Added setTaxUseForInvAgnstPO and getCommodityCodeforAdditionalLines methods
Description    :  		To set taxuse values on invoice from PO/CR and other info on  additional lines for Vertex call

Soumya Mohanty :   		14/11/2011
Change         :		Tax Code Determination on IR
Description    :  		Added Tax Code Determination on IR for Canada Company Code for foreign Ship from

Abhishek Kumar :   		04/08/2013
Change         :		Mach1 Rel5.5 (FRD10.2/TD10.3) Set VATRegistration field on Invoice object for ASN invoice.
Description    :  		Mach1 Rel5.5 (FRD10.2/TD10.3) To set the value from SupplierVAT ID from ASN to VAT Registration field in Invoice Object

Abhishek Kumar :   		08/19/2013
Change         :		Q4 2013 - RSD102 - FDD 2/TDD 2: Set accounting fields in invoice from Ariba Network
Description    :  		Q4 2013 - RSD102 - FDD 2/TDD 2: Set accounting fields from values entered in ASN for invoice against contracts with no accounting specified.

*************************************************************************************/

package config.java.invoicing.sap;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ariba.base.core.Base;
import ariba.base.core.BaseObject;
import ariba.base.core.BaseVector;
import ariba.base.core.ClusterRoot;
import ariba.base.core.Partition;
import ariba.common.core.SplitAccounting;
import ariba.common.core.SplitAccountingCollection;
import ariba.contract.core.Contract;
import ariba.contract.core.ContractLineItem;
import ariba.invoicing.AribaInvoiceReconciliationMethod;
import ariba.invoicing.core.Invoice;
import ariba.invoicing.core.InvoiceLineItem;
import ariba.invoicing.core.Log;
import ariba.procure.core.ProcureLineItem;
import ariba.procure.core.ProcureLineItemCollection;
import ariba.procure.core.ProcureLineType;
import ariba.purchasing.core.DirectOrder;
import ariba.purchasing.core.POLineItem;
import ariba.purchasing.core.PurchaseOrder;
import ariba.purchasing.core.ReqLineItem;
import ariba.purchasing.core.Requisition;
import ariba.receiving.core.ReceivableLineItemCollection;
import ariba.util.core.Date;
import ariba.util.core.ListUtil;
import ariba.basic.core.CommodityCode;
import ariba.basic.core.Country;
import ariba.procure.core.LineItemProductDescription;
//Start: Q4 2013 - RSD102 - FDD 2/TDD 2
import ariba.util.core.StringUtil;
//Start: Q4 2013 - RSD102 - FDD 2/TDD 2

public class CatSAPInvoiceReconciliationMethod extends AribaInvoiceReconciliationMethod
{
	public static final String ClassName = "config.java.invoicing.CatSAPInvoiceReconciliationMethod";

	//Creation of IR
   protected List createInvoiceReconciliations(Invoice invoice)
   {

		Log.customer.debug(ClassName);
		Partition partition = Base.getSession().getPartition();
		BaseVector invLineItems = invoice.getLineItems();
		InvoiceLineItem invLineItem = null;
		BaseVector procureLineItems = null;
		ProcureLineItem pli = null;
		int invLoadingCat = invoice.getLoadedFrom();
		BaseVector newInvLineItems = null;

		ReceivableLineItemCollection appr = null;
		appr = invoice.getOrder();
		if (appr == null)
			appr = invoice.getMasterAgreement();
		//if (appr instanceof DirectOrder)
			//poorder = (DirectOrder) appr;
		//else if (appr instanceof Contract)
			//masteragm = (Contract) appr;

		if (invoice.getTaxDetails().size() == 1)
		{
			invoice.setDottedFieldValue("HeaderTaxDetail", invoice
					.getTaxDetails().get(0));
		}
/*Start-- Added By Rahul--Making CostCenterText, InternalOrderText, WBS Null depending on Account Category */

		List invoiceLineIts = (List) invoice.getFieldValue("LineItems");
		int length = ListUtil.getListSize(invoiceLineIts);

		Log.customer.debug(" CatSAPInvoiceReconciliationMethod invoiceLineItem size => " + length);
		for (int i = 0; i < length; i++)
		{
			InvoiceLineItem invoiceLI = (InvoiceLineItem) invoiceLineIts.get(i);
			Log.customer.debug(" CatSAPInvoiceReconciliationMethod invoiceLine " + invoiceLI);

			String accCat = (String) invoiceLI.getDottedFieldValue("AccountCategory.UniqueName");
			Log.customer.debug(" CatSAPInvoiceReconciliationMethod Account Category " + accCat);
			SplitAccountingCollection sac = invoiceLI.getAccountings();
			if (accCat != null && sac != null)
			{
				Log.customer.debug(" Split Acc is not Null");
				for (Iterator saci = sac.getAllSplitAccountingsIterator(); saci.hasNext();)
				{
					Log.customer.debug("Inside for loop");
					SplitAccounting sa = (SplitAccounting) saci.next();
					if (accCat.equals("F"))
					{
						Log.customer.debug("Inside if condition for Account Category F");
						sa.setDottedFieldValue("CostCenterText", null);
						sa.setDottedFieldValue("WBSElementText", null);
					}
					else if (accCat.equals("K"))
					{
						Log.customer.debug("Inside if condition for Account Category K");
						sa.setDottedFieldValue("InternalOrderText", null);
						sa.setDottedFieldValue("WBSElementText", null);
					}
					else if (accCat.equals("P"))
					{
					    Log.customer.debug("Inside if condition for Account Category P");
					    sa.setDottedFieldValue("InternalOrderText", null);
						sa.setDottedFieldValue("CostCenterText", null);
					}
					else if (accCat.equals("L"))
					{
						Log.customer.debug("Inside if condition for Account Category L");
						sa.setDottedFieldValue("CostCenterText", null);
						sa.setDottedFieldValue("WBSElementText", null);
					}
					else if (accCat.equals("Z"))
					{
					    Log.customer.debug("Inside if condition for Account Category Z");
						sa.setDottedFieldValue("CostCenterText", null);
						sa.setDottedFieldValue("InternalOrderText", null);
						sa.setDottedFieldValue("WBSElementText", null);
					}

				}
			}
		}
//End Code-By Rahul


		// Checking Invoices are Loaded from
		if (invoice.getLoadedFrom() == 3 || invoice.getLoadedFrom() == 4) {
			//if invoice was created using the invoice eform or invoice entry screen (paper invoice)

			invLineItems = invoice.getLineItems();

			invLineItem = null;

			for (int i = 0; i < invLineItems.size(); i++) {
				/***
				For every invoice line item, set the ShipTo, BillingAddress and DeliverTo from the corresponding
				order/MA. Use the first line item on the order/MA since ShipTo and BillTo will be anyway the same
				for all order/MA lines.
				***/
				invLineItem = (InvoiceLineItem) invLineItems.get(i);
				ProcureLineItemCollection plic = invLineItem.getOrder();
				if (plic == null) {
					plic = invLineItem.getMasterAgreement();
				}
				if (plic == null) {
					continue;
				}
				pli = (ProcureLineItem) plic.getLineItems().get(0);
				invLineItem.setShipTo(pli.getShipTo());
				invLineItem.setBillingAddress(pli.getBillingAddress());
				invLineItem.setDeliverTo(pli.getDeliverTo());
			}
		}

		//set the invoice time to 12:00 PM in order to show the same date regardless of the user's location
		Date invoiceDate = invoice.getInvoiceDate();
        // Added the nullcheck on the variable invoiceDate in order to remove the nullPointerException
		if(invoiceDate!=null){
		Date.setHours(invoiceDate, 12);
		invoice.setInvoiceDate(invoiceDate);
		}
		else{
			invoiceDate=Date.getNow();
			Date.setHours(invoiceDate,12);
			invoice.setInvoiceDate(invoiceDate);
	   }


		// Added for ASN Invoice Getting.
		if (invoice.getLoadedFrom() == 1)
		{
				// Set the Block Stamp date to current Date
				invoice.setDottedFieldValue("BlockStampDate",invoiceDate);
				Log.customer.debug(" CatSAPInvoiceReconciliationMethod BlockStampDate => " +invoiceDate);

				// Start: Mach1 R5.5 (FRD10.2/TD10.3)
				if (invoice.getDottedFieldValue("VATTaxDetails") != null)
				{
					Log.customer.debug("CatSAPInvoiceReconciliationMethod Invoice.VATTaxDetails is not null");
					if (invoice.getDottedFieldValue("VATTaxDetails.SupplierVATID") != null)
					{
						Log.customer.debug("CatSAPInvoiceReconciliationMethod Invoice.VATTaxDetails.SupplierVATID is not null. Value is: " +invoice.getDottedFieldValue("VATTaxDetails.SupplierVATID"));
						invoice.setDottedFieldValue("VATRegistration",invoice.getDottedFieldValue("VATTaxDetails.SupplierVATID"));
					    Log.customer.debug("CatSAPInvoiceReconciliationMethod VATRegistration is "+ invoice.getDottedFieldValue("VATRegistration"));
					}
					else
					{
						Log.customer.debug("CatSAPInvoiceReconciliationMethod VATRegistration is null");
					}
				}
				// End: Mach1 R5.5 (FRD10.2/TD10.3)

				// Set Payment Terms at the headed level
				if(invoice.getDottedFieldValue("Order")!=null)
				{
					Log.customer.debug(" CatSAPInvoiceReconciliationMethod Order  => " +invoice.getDottedFieldValue("Order"));
					PurchaseOrder purchaseOrder  = (PurchaseOrder )invoice.getDottedFieldValue("Order") ;
					invoice.setPaymentTerms(purchaseOrder.getPaymentTerms());
				}
				else if(invoice.getDottedFieldValue("MasterAgreement")!=null)
				{
					// Get it from MasterAgreement
					Log.customer.debug(" CatSAPInvoiceReconciliationMethod MasterAgreement  => " +invoice.getDottedFieldValue("MasterAgreement"));
					Contract ma =(Contract) invoice.getDottedFieldValue("MasterAgreement") ;
					invoice.setPaymentTerms(ma.getPaymentTerms());
				}
				//	Set Payment Terms at the headed level


				// Set the Tax Code at Line Item Level from corresponding OrderLine

				List invoiceLineItems = (List)invoice.getFieldValue("LineItems");
				int size = ListUtil.getListSize(invoiceLineItems);
				Log.customer.debug(" CatSAPInvoiceReconciliationMethod invoiceLineItem size => " +size);
				for (int i = 0; i < size; i++)
				{
					BaseObject invoiceLI = (BaseObject)invoiceLineItems.get(i);
					Log.customer.debug(" CatSAPInvoiceReconciliationMethod invoiceLI  => " +invoiceLI);
					if(invoiceLI.getDottedFieldValue("OrderLineItem")!=null)
					{
						Log.customer.debug(" CatSAPInvoiceReconciliationMethod OrderLineItem  => " +invoiceLI.getDottedFieldValue("OrderLineItem"));
						ariba.purchasing.core.POLineItem orderLineItem =(POLineItem) invoiceLI.getDottedFieldValue("OrderLineItem") ;
						invoiceLI.setDottedFieldValue("TaxCode",orderLineItem.getDottedFieldValue("TaxCode"));
					}
					else if(invoiceLI.getDottedFieldValue("MALineItem")!=null)
					{
						// Get it from Contract
						Log.customer.debug(" CatSAPInvoiceReconciliationMethod MALineItem  => " +invoiceLI.getDottedFieldValue("MALineItem"));
						ContractLineItem maLineItem =(ContractLineItem) invoiceLI.getDottedFieldValue("MALineItem") ;
						invoiceLI.setDottedFieldValue("TaxCode",maLineItem.getDottedFieldValue("TaxCode"));
					}else if(invoice.getDottedFieldValue("Order")!=null)
						{
							//	Set Payment Terms at the headed level
							Log.customer.debug(" CatSAPInvoiceReconciliationMethod Order  => " +invoice.getDottedFieldValue("Order"));
							PurchaseOrder purchaseOrder  = (PurchaseOrder )invoice.getDottedFieldValue("Order") ;
							invoiceLI.setDottedFieldValue("PaymentTerms",purchaseOrder.getPaymentTerms());
						}
						else if(invoice.getDottedFieldValue("MALineItem")!=null)
						{
							// Get it from MasterAgreement
							Log.customer.debug(" CatSAPInvoiceReconciliationMethod MasterAgreement  => " +invoice.getDottedFieldValue("MasterAgreement"));
							Contract ma =(Contract) invoice.getDottedFieldValue("MasterAgreement") ;
							invoiceLI.setDottedFieldValue("PaymentTerms",ma.getPaymentTerms());
						}
						//	Set Payment Terms at the headed level

				}

				// Added for reference number _ Issue 955

				//if (Log.customer.debugOn)
			Log.customer.debug("%s ::: Setting the CapsChargeCode and RefLineNumber on inv lines from the linked order lines", ClassName);
		for (int i = 0; i < invLineItems.size(); i++) {
			InvoiceLineItem invLine = (InvoiceLineItem) invLineItems.get(i);
			//ProcureLineItem pli = null;


			if (invLine.getOrderLineItem() != null) {
				pli = invLine.getOrderLineItem();
				//if (Log.customer.debugOn)
					Log.customer.debug("%s ::: Getting the procure line item from the order %s", ClassName, pli);
			}
			else if (invLine.getMALineItem() != null) {
				pli = invLine.getMALineItem();
				//if (Log.customer.debugOn)
					Log.customer.debug("%s ::: Getting the procure line item from the master agreement %s", ClassName, pli);
			}


				if (pli instanceof POLineItem) {
					Log.customer.debug(
							"%s ::: Old Reference Num on the pli is %s",
							ClassName,
							(Integer) pli.getFieldValue("ReferenceLineNumber"));
						PurchaseOrder po = (PurchaseOrder) pli.getLineItemCollection();
						Log.customer.debug("%s ::: Added For Reference : PO is %s",	ClassName,po);
						Requisition req = (Requisition) ((POLineItem) pli).getRequisition();
						Log.customer.debug("%s ::: Added For Reference : Req is %s",	ClassName,req);
						Log.customer.debug("%s ::: Added For Reference : numberonreq is %s",	ClassName,(((POLineItem) pli).getFieldValue("NumberOnReq")));
						ReqLineItem rli1 = (ReqLineItem) req.getLineItem(((Integer) ((POLineItem) pli).getFieldValue("NumberOnReq")).intValue());
						Log.customer.debug("%s ::: Added For Reference : rli1 is %s",	ClassName,rli1);
						Log.customer.debug("%s ::: Added For Reference : ReferenceNumber is %s",	ClassName,((Integer) rli1.getFieldValue("ReferenceLineNumber")).intValue());
						ReqLineItem rli2 = (ReqLineItem) req.getLineItem(((Integer) rli1.getFieldValue("ReferenceLineNumber")).intValue());
						Log.customer.debug("%s ::: Added For Reference : rli2 is %s",	ClassName,rli2);
						POLineItem poli = (POLineItem) po.getLineItem(((Integer) rli2.getFieldValue("NumberOnPO")).intValue());
						Log.customer.debug("%s ::: New Reference Num is %s", ClassName, new Integer(poli.getNumberInCollection()));
						invLine.setFieldValue("ReferenceLineNumber", new Integer(poli.getNumberInCollection()));

					}

					if (pli instanceof ContractLineItem) {
					Log.customer.debug("%s ::: Added For Reference :reference is %s ",	ClassName,pli.getFieldValue("ReferenceLineNumber"));
					invLine.setFieldValue("ReferenceLineNumber", (Integer) pli.getFieldValue("ReferenceLineNumber"));
					Log.customer.debug("%s ::: MLineItem Added For Reference : end ",	ClassName);
				}


			// Added for reference number- Issue 955

		}

		}
		// Added for ASN Invoice Getting.

		if (invLoadingCat == Invoice.LoadedFromACSN || invLoadingCat == Invoice.LoadedFromFile || invLoadingCat == Invoice.LoadedFromUI) {
			//if (Log.customer.debugOn)
				Log.customer.debug("%s ::: The invoice is loaded from ASN or UI or File", ClassName);

			int additionalAC = 0;
			for (int i = 0; i < invLineItems.size(); i++) {
				invLineItem = (InvoiceLineItem) invLineItems.get(i);

				if (invLineItem.getLineType() != null){
					if ((invLineItem.getLineType().getCategory() == ProcureLineType.FreightChargeCategory)
						|| (invLineItem.getLineType().getCategory() == ProcureLineType.DiscountCategory)
						|| (invLineItem.getLineType().getCategory() == ProcureLineType.HandlingChargeCategory)) {
						additionalAC = additionalAC + 1;
					}
				}
			}

			if (additionalAC == 0) {
				//if (Log.customer.debugOn)
					Log.customer.debug("%s ::: Reordering the line items on the invoice", ClassName);

				List lineItems = (List) invoice.getFieldValue("LineItems");
				List orderedLineItems = reorderINVLineItems(lineItems);
				//BaseVector newlines = new BaseVector();
				//newlines.addAll(orderedLineItems);

				try{
					//Log.customer.debug("The clusterroot for the lineitems is - " + invLineItems.getClusterRoot());
					//invoice = (Invoice) invLineItems.getClusterRoot();
					//ClusterRoot cr = (Invoice) invLineItems.getClusterRoot();
					invoice.setFieldValue("LineItems", orderedLineItems);
				}
				catch(Exception e){
					//if (Log.customer.debugOn)
						Log.customer.debug("%s ::: Exception caused at time of setting reordered lines hence lines not reordered", ClassName);
				}
				//invoice.setDottedFieldValue("LineItems", newlines);
				//if (Log.customer.debugOn)
					Log.customer.debug("%s ::: Setting the lineitems field to the new ordered lines vector", ClassName);
			}
			//Start: Q4 2013 - RSD102 - FDD 2/TDD 2
			if (invoice.getMasterAgreement() != null) {
				Log.customer.debug("%s ::: invoice.getMasterAgreement is not null", ClassName);
				Log.customer.debug("%s ::: before calling setSupplierAccountingDistributionOnSplitAccounting method", ClassName);
				setSupplierAccountingDistributionOnSplitAccounting(invLineItems);
				Log.customer.debug("%s ::: after calling setSupplierAccountingDistributionOnSplitAccounting method", ClassName);
			}
			//End: Q4 2013 - RSD102 - FDD 2/TDD 2
		}
		//Code added for Vertex to set TaxUse on additional Lines
		newInvLineItems = invoice.getLineItems();
		if (appr != null)
			setTaxUseForInvAgnstPO(invoice, appr, newInvLineItems);
		//Code for taxUse completed

		// Tax Code Determination - For Foreign - For Non-US (Canada)
		// Tax Code Determination for Non-US (Canada)
		taxCodeDeterminationforNonUS(invoice);

		invoice.save();

		return super.createInvoiceReconciliations(invoice);

	}


	public static List reorderINVLineItems(List lines) {
		List orderedLines = null;
		ArrayList materialLines = new ArrayList();
		ArrayList acLines = new ArrayList();
		ArrayList taxLines = new ArrayList();
		ArrayList unmatchedLines = new ArrayList();
		Integer refNumInt = null;
		Integer invoiceLineNumInt = null;

		//if (Log.customer.debugOn)
			Log.customer.debug("%s ::: Entering the reorderINVLineItems method", ClassName);

		if ((lines != null) && !lines.isEmpty()) {
			int lineCount = lines.size();
			for (int i = 0; i < lineCount; i++) {
				InvoiceLineItem inefli = (InvoiceLineItem) lines.get(i);
				refNumInt = (Integer) inefli.getFieldValue("ReferenceLineNumber");
				//invoiceLineNumInt = (Integer) inefli.getFieldValue("InvoiceLineNumber");

				if ((refNumInt != null) && (refNumInt.intValue() == inefli.getNumberInCollection())) {
					materialLines.add(inefli);
				}
				else if ((refNumInt != null) && (refNumInt.intValue() == 0)) {
					taxLines.add(inefli);
				}
				else {
					acLines.add(inefli);
				}
			}
			int txCount = taxLines.size();
			int mlCount = materialLines.size();
			int aclCount = acLines.size();
			Log.customer.debug("%s ::: Line Counts(Material/AC/Tax): " + mlCount + "/" + aclCount + "/" + txCount, ClassName);
			orderedLines = new ArrayList();
			if (mlCount > 0) {
				//Issue# 708
				for (int j = 0, acindex = 0; j < mlCount; j++) {
					InvoiceLineItem mLine = (InvoiceLineItem) materialLines.get(j);
					int currentOLSize = orderedLines.size();
					//if (Log.customer.debugOn)
						Log.customer.debug(
							"%s ::: Updated Material Ref Num From " + mLine.getDottedFieldValue("ReferenceLineNumber") + "to " + (currentOLSize + 1),
							ClassName);
					mLine.setFieldValue("ReferenceLineNumber", new Integer(currentOLSize + 1));
					orderedLines.add(mLine);
					if (aclCount > 0) {
						// Issue# 708
						for (int k = acindex; k < aclCount; k++) {
							InvoiceLineItem acLine = (InvoiceLineItem) acLines.get(k);
							refNumInt = (Integer) acLine.getFieldValue("ReferenceLineNumber");
							Log.customer.debug("%s ::: refNumInt: %s", ClassName, refNumInt);
							if (refNumInt != null){
								//if (Log.customer.debugOn)
									Log.customer.debug(
										"%s ::: Material Line NIC is: "
											+ mLine.getNumberInCollection()
											+ "compared to AC Ref Number: "
											+ refNumInt.intValue(),
										ClassName);
							}
							if ((refNumInt != null) && (refNumInt.intValue() == mLine.getNumberInCollection())) {
								//if (Log.customer.debugOn)
								// Issue# 708
									acindex = k + 1;
									Log.customer.debug(
										"%s ::: Updated AC Ref Num From "
											+ acLine.getDottedFieldValue("ReferenceLineNumber")
											+ "to "
											+ (currentOLSize + 1),
										ClassName);
								acLine.setFieldValue("ReferenceLineNumber", new Integer(currentOLSize + 1));
								orderedLines.add(acLine);
							}
							// DJS - Added this logic so as to load unmatched additional charge lines from ASN
							else{
								Log.customer.debug("%s ::: Adding line %s to the unmatchedLines List", ClassName, acLine);
								unmatchedLines.add(acLine);
							}
						}
					}
				}
			}
			else {
				return lines;
			}

			// DJS - Added this logic so as to load unmatched additional charge lines from ASN
			if (unmatchedLines.size() > 0){
				int uCount = unmatchedLines.size();
				for (int j = 0; j < uCount; j++) {
					InvoiceLineItem uLine = (InvoiceLineItem) unmatchedLines.get(j);
					if (!orderedLines.contains(uLine)){
						Log.customer.debug("%s ::: Adding line %s from the unmatchedLines List to the ordered lines", ClassName, uLine);
						orderedLines.add(uLine);
					}
				}
			}

			if (txCount > 0) {
				for (int m = 0; m < txCount; m++)
					orderedLines.add((InvoiceLineItem) taxLines.get(m));
			}
		}
		return orderedLines;
	}

	// Code added for Vertex to set TaxUse on invoice lines and other info on additional Lines
	public void setTaxUseForInvAgnstPO(Invoice invoice,
			ReceivableLineItemCollection appr, BaseVector newInvLineItems) {
			LineItemProductDescription des = null;

		Log.customer.debug("%s ::: Entering the setTaxUseForInvAgnstPO method",
				ClassName);
		Log.customer.debug("%s ::: The invoice passed into the method is %s",
				ClassName, invoice);
		Log.customer.debug(
				"%s ::: The order or ma passed into the method is %s",
				ClassName, appr);
		Log.customer
				.debug(
						"%s ::: The invLineItems basevector passed into the method is %s",
						ClassName, newInvLineItems);

		ProcureLineItem plilines = null;
		// Address shipToAddress = null;
		DirectOrder order = null;
		Contract ma = null;
		InvoiceLineItem invLineItemupd = null;
		//BaseObject companycode= null;

		if (appr instanceof DirectOrder)
			order = (DirectOrder) appr;
		else if (appr instanceof Contract)
			ma = (Contract) appr;

		for (int i = 0; i < newInvLineItems.size(); i++) {
			invLineItemupd = (InvoiceLineItem) newInvLineItems.get(i);

			if (order != null) {
				plilines = invLineItemupd.getOrderLineItem();
				Log.customer
						.debug(
								"%s ::: The order line item on the inv line item is %s",
								ClassName, plilines);
			} else {
				plilines = invLineItemupd.getMALineItem();
				Log.customer.debug(
						"%s ::: The MA line item on the inv line item is %s",
						ClassName, plilines);
			}
			Log.customer.debug(
					"%s ::: The order line item on the inv line item is %s",
					ClassName, plilines);

			// if (Log.customer.debugOn)
			Log.customer
					.debug(
							"%s ::: Setting the line level TaxUse field on the invoice line",
							ClassName);
			if (plilines != null) {
				invLineItemupd.setFieldValue("TaxUse", (ClusterRoot) plilines
						.getFieldValue("TaxUse"));

			} else {
			Log.customer.debug(	"CatSAPInvoiceReconciliationMethod::: Check for additional line Category getCategory()"+invLineItemupd.getLineType().getCategory());

			     if(!(invLineItemupd.getLineType().getCategory() == ProcureLineType.TaxChargeCategory))
				 {
				Log.customer.debug(	"%s ::: Line item is additional Lines on the invoice",ClassName);
				InvoiceLineItem firstLineOnInv = (InvoiceLineItem) newInvLineItems.get(0);
				Log.customer.debug(	"%s ::: 1st line from Invoice firstLineOnInv %s",ClassName,firstLineOnInv);
					if (firstLineOnInv != null) {
                         Log.customer.debug("%s ::: setting values from 1st line",ClassName);
						invLineItemupd.setFieldValue("TaxUse",(ClusterRoot) firstLineOnInv.getFieldValue("TaxUse"));
						invLineItemupd.setFieldValue("LineItemType","Service Only (TQS)");
						Log.customer.debug(	"%s ::: LineItemType on additional lines %s",ClassName,invLineItemupd.getFieldValue("LineItemType"));
						invLineItemupd.setFieldValue("TaxCode",(ClusterRoot) firstLineOnInv.getFieldValue("TaxCode"));
						invLineItemupd.setFieldValue("IncoTerms1",(ClusterRoot) firstLineOnInv.getFieldValue("IncoTerms1"));

					}

					CommodityCode CommCode = getCommodityCodeforAdditionalLines(invLineItemupd);
					Log.customer.debug(	"%s ::: CommodityCode %s",ClassName,CommCode);
					des = (LineItemProductDescription)invLineItemupd.getDottedFieldValue("Description");
					Log.customer.debug(	"%s ::: LineItem description %s",ClassName,des);
					if(des != null)
					des.setFieldValue("CommonCommodityCode",CommCode);

				}
				// */
			}
		}

	}

	public CommodityCode getCommodityCodeforAdditionalLines ( InvoiceLineItem invLineItemupd)
	{
     Log.customer.debug(	"%s to get commodity code for additional lines getCommodityCodeforAdditionalLines",ClassName);
	final String Freightcode   = "AC0204";
		 //String Freightcode   = "11121802";
	final String Handlingcode  = "AC0207";
	 	 	// String Handlingcode  = "11121803";
	final String SpecialChargescode = "AC0210";
	 	 //CR:needs to be final
	 //String SpecialChargescode = "11121806";
	 CommodityCode gcc = null;
	  //Partition partition = invLineItemupd.getPartition();
	  //Log.customer.debug("%s inside getCommodityCodeforAdditionalLines method and partition is %s",ClassName,partition);
	 if(invLineItemupd.getLineType().getCategory() == ProcureLineType.FreightChargeCategory)
	 {
	 Log.customer.debug("%s inside getCommodityCodeforAdditionalLines method and line type is Freight is ",ClassName);
	 gcc = (CommodityCode)Base.getService().objectMatchingUniqueName("ariba.basic.core.CommodityCode",
	                        Base.getService().getPartition("None"),Freightcode);

	Log.customer.debug("%s inside getCommodityCodeforAdditionalLines method and commoditycode is %s",ClassName,gcc);
							return gcc;
		}
else if(invLineItemupd.getLineType().getCategory() == ProcureLineType.SpecialChargeCategory)
	 {
	 Log.customer.debug("%s inside getCommodityCodeforAdditionalLines method and line type is Freight is ",ClassName);
	gcc = (CommodityCode)Base.getService().objectMatchingUniqueName("ariba.basic.core.CommodityCode",
	                        Base.getService().getPartition("None"),SpecialChargescode);
	Log.customer.debug("%s inside getCommodityCodeforAdditionalLines method and commoditycode is %s ",ClassName,gcc);
							return gcc;
		}
else if(invLineItemupd.getLineType().getCategory() == ProcureLineType.HandlingChargeCategory)
	 {
	 Log.customer.debug("%s inside getCommodityCodeforAdditionalLines method and line type is Freight is ",ClassName);
	gcc = (CommodityCode)Base.getService().objectMatchingUniqueName("ariba.basic.core.CommodityCode",
	                        Base.getService().getPartition("None"),Handlingcode);
	Log.customer.debug("%s inside getCommodityCodeforAdditionalLines method and commoditycode is %s",ClassName,gcc);
							return gcc;
		}
else return null;
	}

	// Tax Code Determination for Non-US (Canada)

	public void taxCodeDeterminationforNonUS(Invoice invoice)
	{
		// Declaring Method Members/ Variables
		String companyCode = "";
		String companyCodeCountry = "";
		String checkCallToVertex = "";
		String shipTo_country = "";
		String shipFrom_country = "";
		String sapTaxCodeForLineItem = "";
		//////////////

		if(invoice != null) // && ir.getFieldValue() - Need to check the Lineitem number > 0???
		{
			/////////////////////////////////////////////////////////////////////////
			companyCode = (String) invoice.getDottedFieldValue("CompanyCode.UniqueName");
			Log.customer.debug(" CatMSCTaxCodeDetermination : INVOICE object Company Code Value =" + companyCode);
			checkCallToVertex = (String) invoice.getDottedFieldValue("CompanyCode.CallToVertexEnabled");
			Log.customer.debug(" CatMSCTaxCodeDetermination : INVOICE object Company Code CallToVertexEnabled Value =" + checkCallToVertex);
			companyCodeCountry = (String) invoice.getDottedFieldValue("CompanyCode.RegisteredAddress.Country.UniqueName");
			Country companyCodeCountryobj = (Country) invoice.getDottedFieldValue("CompanyCode.RegisteredAddress.Country");
			Log.customer.debug(" CatMSCTaxCodeDetermination : INVOICE object Company Code Country Value =" + companyCodeCountry);
			/////////////////////////////////////////////////////////////////////////
			if(companyCodeCountry == "CA" && checkCallToVertex !=null && (checkCallToVertex.equals("IR") || checkCallToVertex.equals("PIB")))
			{
				Log.customer.debug(" CatMSCTaxCodeDetermination : INVOICE: companyCodeCountry is CA");
				// BaseVector irlines = invoice.getLineItems();
				BaseVector invLineItems = invoice.getLineItems();
				int tempPrintCount = invLineItems.size();
				for (int countFilterList = 0; countFilterList < tempPrintCount; countFilterList++)
				{
					//InvoiceReconciliationLineItem irli = (InvoiceReconciliationLineItem)invLineItems.get(countFilterList);
					InvoiceLineItem invoiceLI = (InvoiceLineItem)invLineItems.get(countFilterList);
					// InvoiceLineItem invoiceLI = (InvoiceLineItem) invoiceLineIts.get(i);

					shipTo_country = (String) invoiceLI.getDottedFieldValue("ShipTo.Country.UniqueName");
					Log.customer.debug(" CatMSCTaxCodeDetermination : INVOICE lineitem ShipTo Country = "+ shipTo_country);

					shipFrom_country = (String) invoiceLI.getDottedFieldValue("ShipFrom.Country.UniqueName");
					Log.customer.debug(" CatMSCTaxCodeDetermination : INVOICE lineitem ShipFrom Country = "+ shipFrom_country);

					sapTaxCodeForLineItem = (String) invoiceLI.getDottedFieldValue("TaxCode.SAPTaxCode");
					Log.customer.debug(" CatMSCTaxCodeDetermination : INVOICE: SAPTAXCODEFOR LINE ITEM = " + sapTaxCodeForLineItem);

					if(sapTaxCodeForLineItem !=null && shipTo_country =="CA" && shipFrom_country != "CA")
					{
						if(!(sapTaxCodeForLineItem.equalsIgnoreCase("IM")))
						{
							Log.customer.debug(" CatMSCTaxCodeDetermination : INVOICE: ShipTo Country is CA and ShipFrom Country is Non-CA");
							Object[] taxCodelookupKeys = new Object[2];
							taxCodelookupKeys[0] = "IM";
							Log.customer.debug(" CatMSCTaxCodeDetermination : INVOICE: TaxCode from the lookup "+taxCodelookupKeys[0]);

							taxCodelookupKeys[1] =  companyCodeCountryobj;
							Log.customer.debug(" CatMSCTaxCodeDetermination : INVOICE: Country " + shipTo_country);
							ClusterRoot taxcode = (ClusterRoot) Base.getSession().objectFromLookupKeys(taxCodelookupKeys, "ariba.tax.core.TaxCode", invoice.getPartition());
							// Set the Value of LineItem.TaxCode.UniqueName = 'IM'
							Log.customer.debug(" CatMSCTaxCodeDetermination : INVOICE: Setting TaxCode to IM as Foreign TaxCode");
							invoiceLI.setDottedFieldValue("TaxCode", taxcode);
							// Set the Value of LineItem.TaxCode.SAPTaxCode = 'IM'
							Log.customer.debug(" CatMSCTaxCodeDetermination : INVOICE: Setting TaxCode to IM due to Foreign ShipTo, COMPLETED");
						}
					}
				}
			}
		}
	}
	//Start: Q4 2013 - RSD102 - FDD 2/TDD 2
	public void setSupplierAccountingDistributionOnSplitAccounting(BaseVector invLineItems) {
		InvoiceLineItem invLineItem = null;
		Boolean flag = false;

		for (int i = 0; i < invLineItems.size(); i++) {
			invLineItem = (InvoiceLineItem) invLineItems.get(i);
			flag = false; // To reset the value of flag to false for every new LineItem under check.

				Log.customer.debug("%s ::: Displaying Accounting distribution Before SetSuppAcctDist - Start", ClassName);
				for (int j = 0; j < invLineItem.getAccountings().getSplitAccountings().size(); j++)
				{
					SplitAccounting sa2 = (SplitAccounting)invLineItem.getAccountings().getSplitAccountings().get(j);
					Log.customer.debug("%s ::: Displaying Accounting distribution - GeneralLedgerText: %s", ClassName, sa2.getFieldValue("GeneralLedgerText"));
					Log.customer.debug("%s ::: Displaying Accounting distribution - CostCenterText: %s", ClassName, sa2.getFieldValue("CostCenterText"));
					Log.customer.debug("%s ::: Displaying Accounting distribution - WBSElementText: %s", ClassName, sa2.getFieldValue("WBSElementText"));
					Log.customer.debug("%s ::: Displaying Accounting distribution - InternalOrderText: %s", ClassName, sa2.getFieldValue("InternalOrderText"));

					// Code to check for null Accountings in the invoices or Contract. At this point the Accounting is defaulted from Contract.

					if((StringUtil.nullOrEmptyOrBlankString((String)(sa2.getFieldValue("GeneralLedgerText")))) && (StringUtil.nullOrEmptyOrBlankString((String)(sa2.getFieldValue("CostCenterText")))))
					{
					    flag = true;
					}

				}
				Log.customer.debug("%s ::: Displaying Accounting distribution Before SetSuppAcctDist - End", ClassName);

				if(flag == true)
				{
					Log.customer.debug("One or more fields in Account Distribution is empty.. Considering ASN Accounting now", ClassName);


						if ((!StringUtil.nullOrEmptyOrBlankString((String)invLineItem.getFieldValue("SuppAcctDistGL")))
							|| (!StringUtil.nullOrEmptyOrBlankString((String)invLineItem.getFieldValue("SuppAcctDistCostCenter")))
							|| (!StringUtil.nullOrEmptyOrBlankString((String)invLineItem.getFieldValue("SuppAcctDistInternalOrder")))
							|| (!StringUtil.nullOrEmptyOrBlankString((String)invLineItem.getFieldValue("SuppAcctDistWBS"))))
						{

							SplitAccounting sa = (SplitAccounting)invLineItem.getAccountings().getSplitAccountings().get(0);
							Log.customer.debug("%s ::: Setting ASN Accounting to Invoice as Contract Accountings are null", ClassName);
							sa.setFieldValue("GeneralLedgerText", invLineItem.getFieldValue("SuppAcctDistGL"));
							sa.setFieldValue("CostCenterText", invLineItem.getFieldValue("SuppAcctDistCostCenter"));
							sa.setFieldValue("InternalOrderText", invLineItem.getFieldValue("SuppAcctDistInternalOrder"));
							sa.setFieldValue("WBSElementText", invLineItem.getFieldValue("SuppAcctDistWBS"));

							sa.setPercentage(invLineItem.getAccountings().getTotalPercentage());
							sa.setQuantity(invLineItem.getAccountings().getTotalQuantity());
							sa.setAmount(invLineItem.getAccountings().getTotalAmount());

							Log.customer.debug("%s ::: totals are set to first split accounting", ClassName);

							invLineItem.getAccountings().getSplitAccountings().clear();
							Log.customer.debug("%s :::: Removed all splits from the collection", ClassName);

							invLineItem.getAccountings().getSplitAccountings().add(0, sa);
							Log.customer.debug("%s ::: Added the new split accounting", ClassName);

							Log.customer.debug("%s ::: Displaying Accounting distribution After SetSuppAcctDist - Start", ClassName);
							for (int j = 0; j < invLineItem.getAccountings().getSplitAccountings().size(); j++) {
								SplitAccounting sa2 = (SplitAccounting)invLineItem.getAccountings().getSplitAccountings().get(j);
								Log.customer.debug("%s ::: Displaying Accounting distribution - GeneralLedgerText: %s", ClassName, sa2.getFieldValue("GeneralLedgerText"));
								Log.customer.debug("%s ::: Displaying Accounting distribution - CostCenterText: %s", ClassName, sa2.getFieldValue("CostCenterText"));
								Log.customer.debug("%s ::: Displaying Accounting distribution - InternalOrderText: %s", ClassName, sa2.getFieldValue("InternalOrderText"));
								Log.customer.debug("%s ::: Displaying Accounting distribution - WBSElementText: %s", ClassName, sa2.getFieldValue("WBSElementText"));
							}
							Log.customer.debug("%s ::: Displaying Accounting distribution After SetSuppAcctDist - End", ClassName);
						}
				}
		}
	}
//End: Q4 2013 - RSD102 - FDD 2/TDD 2
}
