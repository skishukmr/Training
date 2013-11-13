/*
 Author: Nani Venkatesan (Ariba Inc.)
   Date; 5/29/2005
Purpose: The purpose of this class is to override the OOB reconciliation engine.

Change Log:
08/13/2009		Vikram	   CR189		Auto-Reject
19/10/2009      Vikram 	   CR189		Allow credit invoices
12/11/2009      Vikram     CR189		Added extra check for contract close variance
19/05/2010		Vikram	   Issue 1120	Removing Auto accepting/rejecting logic for Freight Variance
13/04/2011      Samir                   Removing multiple cleared tax exceptions from the IR. Ensuring
                                        that only one tax exception with cleared status show up if
                                        applicable
21/06/2011      Aswini		Vertex		copy New fields from Eform to IR
28/10/2011      Aswini		Vertex		Added 2  conditions to check CallToVertexEnabled values to remove OVER_TAX variance exception
29/11/2011 		Aswini 		Vertex 		To remove Vertex tax exception for all the IRs
20/01/2012 		Divya 		Vertex 		New method checkIfIRHasInvalidTaxAmount added which is called only if callToVertexisEnabled is true
										This method will set the CATTaxCalculatioNFailed exception to true  if
										supplier tax amount is added for B0 and B4 taxcodes
26/01/2012 		Divya 		Vertex 		The condition TAX_CALC_FAIL that was included in mergeExceptions has been removed to retain the fix
										for multiple tax exceptions
27/01/2012 		Divya 		Vertex 		Included B3 taxcode along with B0 and B4 as condition within  method: checkIfIRHasInvalidTaxAmount
08/02/2012 		Divya 		Vertex 		Included null check for TaxAmount field in the method checkIfIRHasInvalidTaxAmount
190  IBM AMS_Lekshmi  Auto Rejecting InvoiceReconciliation if the Order/Contract Currency different from Invoice Currency
08/08/2012      IBM AMS_Manoj   WI 295          Close Order variance is added based on the value of Closed field.
10/08/2012		IBM AMS_Vikram	WI 320	IR call to validate accounting and pull in people to the approvable and stop the IR for wrong accounting
02/21/2013      IBM Niraj  Mach1 R5.5 (FRD1.2/TD1.2) Commented to allow multiple tax line.
02/21/2013      IBM Niraj  Mach1 R5.5 (FRD2.4/TD2.4) Logic added to allow the invoice exceptions for the Marca da bollo line
02/21/2013      IBM Niraj  Mach1 R5.5 (FRD2.5/TD2.5) Logic added to auto reject the invoice if there is more than 1 unmatched line
02/21/2013      IBM Niraj  Mach1 R5.5 (FRD2.6/TD2.6) Logic added to set GL account of Marca da bollo line to 5602001000
02/21/2013      IBM Niraj  Mach1 R5.5 (FRD2.7/TD2.7) Logic added to set unspspc code of Marca da bollo line to 931617
02/21/2013      IBM Niraj  Mach1 R5.5 (FRD2.8/TD2.8) Logic added to allow new marca da bollo line for invoice eform.
08/22/2013	Parita Shah Q4 2013 - RSD111 - FDD 3.0/TDD 1.1 Logic added to auto reject IRs if Invoice shipto does not match PO/Contract Shipto
30/08/2013	IBM Nandini  Q4 2013-RSD119-FDD5.0/TDD1.0 Logic added to reject Invoices that were raised against OIO enabled POs.
*/

package config.java.invoicing.sap;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;

import ariba.approvable.core.Approvable;
import ariba.procure.core.ProcureLineItemCollection;
import ariba.base.core.Base;
import ariba.base.core.BaseId;
import ariba.base.core.BaseObject;
import ariba.base.core.BaseVector;
import ariba.base.core.ClusterRoot;
import ariba.base.core.LongString;
import ariba.base.core.Partition;
import ariba.base.core.aql.AQLOptions;
import ariba.base.core.aql.AQLQuery;
import ariba.base.core.aql.AQLResultCollection;
import ariba.base.core.aql.AQLScalarExpression;
import ariba.basic.core.Currency;
import ariba.basic.core.Money;
// Start :  Q4 2013 - RSD111 - FDD 3.0/TDD 1.1
import ariba.common.core.Address;
// End :  Q4 2013 - RSD111 - FDD 3.0/TDD 1.1
import ariba.basic.core.PostalAddress;
import ariba.common.core.Supplier;
import ariba.contract.core.Contract;
import ariba.contract.core.ContractCoreApprovable;
import ariba.contract.core.ContractLineItem;
import ariba.invoicing.core.Invoice;
import ariba.invoicing.core.InvoiceException;
import ariba.invoicing.core.InvoiceExceptionType;
import ariba.invoicing.core.InvoiceLineItem;
import ariba.invoicing.core.InvoiceReconciliation;
import ariba.statement.core.StatementReconciliation;
import ariba.invoicing.core.InvoiceReconciliationLineItem;
import ariba.statement.core.StatementReconciliationLineItem;
import ariba.invoicing.core.Log;
import ariba.procure.core.ProcureLineItem;
import ariba.procure.core.ProcureLineType;
import ariba.purchasing.core.PurchaseOrder;
import ariba.reconciliation.core.ReconciliationException;
import ariba.tax.core.TaxDetail;
import ariba.user.core.User;
import ariba.util.core.Assert;
import ariba.util.core.Date;
import ariba.util.core.Fmt;
import ariba.util.core.ListUtil;
import ariba.util.core.ResourceService;
import ariba.util.core.StringUtil;
import ariba.util.core.SystemUtil;
import ariba.util.formatter.BigDecimalFormatter;
import ariba.util.formatter.DoubleFormatter;
import ariba.util.formatter.IntegerFormatter;
import config.java.common.sap.CATSAPUtils;
import config.java.common.sap.CatSAPTaxUtil;
import config.java.condition.sap.CatSAPAdditionalChargeLineItem;
import config.java.condition.sap.CatSAPValidReferenceLineNumber;
import config.java.invoicing.CatInvoiceReconciliationEngine;
import config.java.invoicing.vcsv1.CatCSVInvoiceSubmitHook;
import config.java.tax.CatTaxUtil;
//Start: Mach1 R5.5 (FRD2.4/TD2.4)
import ariba.common.core.SplitAccounting;
import ariba.common.core.SplitAccountingCollection;
import config.java.invoiceeform.sap.CatSAPInvoiceEformSubmitHook;
//End: Mach1 R5.5 (FRD2.4/TD2.4)

public class CatSAPInvoiceReconciliationEngine extends CatInvoiceReconciliationEngine
{
	private static final String QUANTITY = "POQuantityVariance";
	private static final String RECEIVED_QUANTITY = "POReceivedQuantityVariance";
	private static final String CLOSE_ORDER = "ClosePOVariance";
	private static final String TAX_CALC_FAIL = "CATTaxCalculationFailed";
	private static final String FREIGHT_VARIANCE = "FreightVariance";
	private static final String SPECIAL_VARIANCE = "SpecialVariance";
	private static final String UNMATCHED_INVOICE = "UnmatchedInvoice";
	private static final String MA_NOT_INVOICABLE = "MANotInvoiceable";
	private static final String OVER_TAX = "OverTaxVariance";
	private static final String PO_RCVD_Q_V = "POReceivedQuantityVariance";
	private static final String MA_RCVD_Q_V = "MAReceivedQuantityVariance";
	private static final String PO_PRC_V = "POPriceVariance";
	private static final String PO_CATPRC_V = "POCatalogPriceVariance";
	private static final String PO_Line_AMT_V = "POLineAmountVariance";
	private static final String MA_NC_PRC_V = "MANonCatalogPriceVariance";
	private static final String MA_CAT_PRC_V = "MACatalogPriceVariance";
	private static final String MA_Line_AMT_V = "MALineAmountVariance";
	private static final String CAT_INVALID_ACCTNG = "AccountingDistributionException";
	private static final String CANCELLED_ORDER = "CancelledPOVariance";
	private static final String CLOSED_CONTRACT = "MACloseVariance";
	private static final String TRANS_COMP_VAR = "TransactionCompletedVariance";
	private static final String ZERO_AMOUNT="ZeroAmountVariance";
	private static final String MA_NOT_INVOICING = "MANotInvoicing";
	private static final String MA_LINE_DATE_VAR = "MALineDateVariance";
	private static final String GENERIC_TAX_CALC_FAIL = "TaxCalculationFailed";
	private static final String VERTEX_TAX_CAL_FAIL = "VertexTaxVariance";
	public static final String ComponentStringTable = "cat.java.vcsv1.csv";
	//Start: Mach1 R5.5 (FRD2.4/TD2.4)
	private static final String UNMATCHED_LINE = "UnmatchedLine";
    //End: Mach1 R5.5 (FRD2.4/TD2.4)
	private String TAX_CALC_FAIL_FLAG_FOR_INVALID_TXAMT = "false";

	private static final String UnitPriceTolerance = Fmt.Sil("cat.java.sap","LineUnitPriceTolerenceAllowedInPercent");
    private static double unitPriceTolerencePctDouble = 0.0;

	//private static final String[] freightSupplierArr = StringUtil.delimitedStringToArray(Fmt.Sil("cat.java.sap","SuppliersWhoCanChargeFreight"), ':');
	private static final String LineAmountTolerance = Fmt.Sil("cat.java.sap","LineAmountTolerenceAllowedInDollars");

    private static final BigDecimal lineAmountTolerenceInDollars = new BigDecimal(LineAmountTolerance);

	private String[] autoRejectExcTypes = { UNMATCHED_INVOICE, MA_NOT_INVOICABLE };
    //Start: Mach1 R5.5 (FRD2.4/TD2.4)
	private String[] ignoreExceptions = { OVER_TAX, PO_RCVD_Q_V, MA_RCVD_Q_V, UNMATCHED_LINE };
    //End: Mach1 R5.5 (FRD2.4/TD2.4)
	//Quantity Received Exception Types - want to remove for negative invoices
	private String[] receiptExcTypes = {QUANTITY, RECEIVED_QUANTITY};
	private static final String StringTable = "cat.java.common";
    //Start: Mach1 R5.5 (FRD2.5/TD2.5)
	public int additionaleFormLinesPresent = 0;
    //End: Mach1 R5.5 (FRD2.5/TD2.5)
	public boolean reconcile(Approvable approvable)
	{
    		Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB additionaleFormLinesPresent START=> " +additionaleFormLinesPresent);
            if (approvable instanceof InvoiceReconciliation) {
			setHeaderTaxDetail(approvable);

			// Coded Added by Majid to copy the SAP specific field from Invoice to IR object - starts here
			InvoiceReconciliation ir = (InvoiceReconciliation)approvable;

			if (ir.isCreationInProgress()){
					Log.customer.debug("%s ::: Running through to default from Invoice this is IR Creation.", ClassName);

				// copy Cat-specific fields from INV to IR
				copyFieldsFromInvoice(ir);
			}
			else{
					Log.customer.debug("%s ::: IR not in Creation process!", ClassName);
				// must recopy TaxAmount from Invoice lines
				recopyFieldsFromInvoice(ir);
			}
			//Coded Added by Majid to copy the SAP specific field from Invoice to IR object - ends here

		}
		return super.reconcile(approvable);
	}


	// Method added by Majid - starts here

	private void copyFieldsFromInvoice(InvoiceReconciliation ir) {
        Partition partition = ir.getPartition();
		Log.customer.debug("%s ::: Entering the method copyFieldsFromInvoice", ClassName);
		Invoice invoice = ir.getInvoice();
		//Added by nagendra
		 List invoiceLineItems = (List)invoice.getFieldValue("LineItems");
		 int size = ListUtil.getListSize(invoiceLineItems);

		ir.setFieldValue("BlockStampDate", (Date) invoice.getFieldValue("BlockStampDate"));

		// Added by Majid to set the Invoice Date from Invoice to IR object.
		ir.setFieldValue("InvoiceDate", (Date) invoice.getFieldValue("InvoiceDate"));

		if(invoice.getFieldValue("RelatedCatInvoice")!= null)
	  	{
			Log.customer.debug("RelatedCatInvoice value is " + invoice.getFieldValue("RelatedCatInvoice"));
			ir.setFieldValue("RelatedCatInvoice",(String)invoice.getFieldValue("RelatedCatInvoice"));
		}else
			{
			Log.customer.debug("RelatedCatInvoice value is null");
			}

		if(invoice.getFieldValue("WithHoldTaxCode")!= null)
	  	{
			Log.customer.debug("WithHoldTaxCode value is " + invoice.getFieldValue("WithHoldTaxCode"));
			ir.setFieldValue("WithHoldTaxCode",(ClusterRoot)invoice.getFieldValue("WithHoldTaxCode"));
		}
		if(invoice.getTotalTax()!=null){
			ir.setTotalTax(invoice.getTotalTax());
		}
	    if(invoice.getFieldValue("CurrencyExchangeRate")!= null)
	  	{
			Log.customer.debug("CurrencyExchangeRate value is " + invoice.getFieldValue("CurrencyExchangeRate"));
			ir.setFieldValue("CurrencyExchangeRate",(String)invoice.getFieldValue("CurrencyExchangeRate"));
		}

			//Nagendra added
			for (int i = 0; i < size; i++) {
				BaseObject invoiceLI = (BaseObject)invoiceLineItems.get(i);
				InvoiceReconciliationLineItem irLi = new InvoiceReconciliationLineItem(partition, ir);
				if(invoiceLI.getFieldValue("CustomSuppLoc")!= null)
				irLi.setFieldValue("CustomSuppLoc",(PostalAddress)invoiceLI.getFieldValue("CustomSuppLoc"));
				Log.customer.debug(" CusSupplierlocation value => " + invoiceLI.getFieldValue("CustomSuppLoc"));
				if(invoiceLI.getFieldValue("TaxCode")!= null)
					irLi.setFieldValue("TaxCode",(ClusterRoot)invoiceLI.getFieldValue("TaxCode"));
				Log.customer.debug(" invoiceLI TaxAmount value => " + invoiceLI.getFieldValue("TaxAmount"));
				if(invoiceLI.getFieldValue("TaxAmount")!= null)
					Log.customer.debug(" invoiceLI TaxAmount value => " + invoiceLI.getFieldValue("TaxAmount"));
					irLi.setDottedFieldValue("TaxAmount",(Money)invoiceLI.getFieldValue("TaxAmount"));
					Log.customer.debug(" irLi TaxAmount value => " + irLi.getFieldValue("TaxAmount"));
				if(invoiceLI.getFieldValue("TransportMode")!= null)
				irLi.setFieldValue("TransportMode",(ClusterRoot)invoiceLI.getFieldValue("TransportMode"));
				Log.customer.debug(" TransportMode value => " + invoiceLI.getFieldValue("TransportMode"));
				if(invoiceLI.getFieldValue("TransactionNature")!= null)
				irLi.setFieldValue("TransactionNature",(ClusterRoot)invoiceLI.getFieldValue("TransactionNature"));
				Log.customer.debug(" TransactionNature value => " + invoiceLI.getFieldValue("TransactionNature"));
				if(invoiceLI.getFieldValue("NetWeight")!=null)
                 irLi.setFieldValue("NetWeight",(String)invoiceLI.getFieldValue("NetWeight"));
			     Log.customer.debug(" NetWeight value => " + invoiceLI.getFieldValue("NetWeight"));
			}
			//end-nagendra

			boolean isTaxExcpReqd = CatSAPTaxUtil.compareTax(ir);
			if(isTaxExcpReqd){
				ir.setFieldValue("taxCallNotFailed", new Boolean(false));
			}
		Log.customer.debug("%s ::: Exitting the method copyFieldsFromInvoice", ClassName);
}
	// Method added by Majid - Ends here

	private void recopyFieldsFromInvoice(InvoiceReconciliation ir) {

		Log.customer.debug("%s ::: ENTERING the method reCopyFieldsFromInvoice", ClassName);

		BaseVector irLineItems = ir.getLineItems();
		InvoiceReconciliationLineItem irli = null;
		InvoiceLineItem invli = null;

		for (int i = 0; i < irLineItems.size(); i++) {
			irli = (InvoiceReconciliationLineItem) irLineItems.get(i);
			invli = irli.getInvoiceLineItem();

			irli.setTaxAmount(invli.getTaxAmount());
			ProcureLineType plt = irli.getLineType();
			if (plt == null || plt.getCategory() == ProcureLineType.LineItemCategory) {
				//if (Log.customer.debugOn)
					Log.customer.debug("%s ::: Material Line - setting AccountCategory!", ClassName);
			    ProcureLineItem pli = getProcureLineItem (irli);
			    if (pli != null) {
					irli.setDottedFieldValueWithoutTriggering("AccountCategory", pli.getFieldValue("AccountCategory"));
					irli.setFieldValue("TaxUse", (ClusterRoot) pli.getFieldValue("TaxUse"));
			    }
			}
		}
		//if (Log.customer.debugOn)
			Log.customer.debug("%s ::: EXITING the method reCopyFieldsFromInvoice", ClassName);
	}

	public static ProcureLineItem getProcureLineItem(InvoiceReconciliationLineItem irli) {

	    //if (Log.customer.debugOn) {
			Log.customer.debug("%s ::: Entering method getProcureLineItem", ClassName);
		//}
		ProcureLineItem pli = irli.getOrderLineItem();
		if (pli == null) {
			//if (Log.customer.debugOn)
				Log.customer.debug("%s ::: Getting InvoiceLineItem since no POLineItem.", ClassName);
			if (irli.getMasterAgreement() != null) {
				InvoiceLineItem ili = irli.getInvoiceLineItem();
				if (ili != null)
				    pli = ili.getMALineItem();
			}
		}
		//if (Log.customer.debugOn)
			Log.customer.debug("%s ::: PLI returned from getProcureLineItem(): %s", ClassName,pli);
		return pli;
	}


    protected boolean validateHeader(Approvable approvable)
    {
        Assert.that(approvable instanceof InvoiceReconciliation, "%s.validateHeader: approvable must be an IR", "config.java.invoicing.sap.CatSAPInvoiceReconciliationEngine");
        InvoiceReconciliation ir = (InvoiceReconciliation)approvable;
        Log.customer.debug("\n\n\n");
        Log.customer.debug("%s ::: Calling the checkForDuplicateInvoice() method in validateHeader()", "CatInvoiceReconciliationEngine");
        checkForDuplicateInvoice(ir);
        Log.customer.debug("%s ::: Done calling the checkForDuplicateInvoice() method in validateHeader()", "CatInvoiceReconciliationEngine");
        Log.customer.debug("\n\n\n");
        Log.customer.debug("%s.validateHeader called with %s", "config.java.invoicing.sap.CatSAPInvoiceReconciliationEngine", ir);

		if ((ir.getInvoice().isCreditMemo()) || (ir.getInvoice().isDebitMemo())) {

			//reject credit and debit memos. shouldAutoReject method does not get called for credit and debit memos and
			//hence not used!
			Log.customer.debug("Rejecting credit and debit memos ...");
			ir.reject();
		}
		if (ir.getInvoice().getInvoiceOperation().equals(Invoice.OperationDelete)) {
			//reject replacement invoices
			Log.customer.debug("Rejecting replacement/cancel invoice ...");
			ir.reject();
		}

		//Check if IR To Be Rejected then auto reject it.
		checkIfIRToBeRejected(ir);
		// Start Of Issue 190
		autoRejectInvoiceCurrencyDiffFromOrderOrContract(ir);
		// issue 190 End

		//Vertex change to include exception handler for B0,B3,B4 tax code with 0 supplier Amount
		if((ir.getDottedFieldValue("CompanyCode.CallToVertexEnabled")!=null) &&((((String) ir.getDottedFieldValue("CompanyCode.CallToVertexEnabled")).equals("IR"))||(((String) ir
					.getDottedFieldValue("CompanyCode.CallToVertexEnabled")).equals("PIB")))){
		checkIfIRHasInvalidTaxAmount(ir);
		}
		//End of Vertex change for tax code exception handler


		return super.validateHeader(approvable);
    }



    private void checkForDuplicateInvoice(BaseObject parent) {
		Boolean notDuplicate = new Boolean(true);
		InvoiceReconciliation ir = (InvoiceReconciliation) parent;
        if(ir.isCreationInProgress())
        {
            //if (Log.customer.debugOn)
                Log.customer.debug("%s ::: Is Creation of the IR in progress " + ir.isCreationInProgress(), "config.java.invoicing.sap.CatSAPInvoiceReconciliationEngine");
        } else
        {
            //if (Log.customer.debugOn)
            //{
                Log.customer.debug("%s ::: Is Creation of the IR in progress " + ir.isCreationInProgress(), "config.java.invoicing.sap.CatSAPInvoiceReconciliationEngine");
                Log.customer.debug("%s ::: Returning out of the checkForDuplicateInvoice() without checking for duplicate", "config.java.invoicing.sap.CatSAPInvoiceReconciliationEngine");
            //}
            return;
        }
		ir.setDottedFieldValue("IsNotDuplicate", notDuplicate);
		ir.getInvoice().setDottedFieldValue("IsNotDuplicate", notDuplicate);
        Supplier supplier = ir.getInvoice().getSupplier();
        String supplierInvoiceNumber = ir.getInvoice().getInvoiceNumber();
        //if (Log.customer.debugOn)
        {
            Log.customer.debug("%s ::: The Inv # object is %s", "config.java.invoicing.sap.CatSAPInvoiceReconciliationEngine", supplierInvoiceNumber);
        }
        if (supplier == null || StringUtil.nullOrEmptyOrBlankString(supplierInvoiceNumber)) {
            //if(Log.customer.debugOn) {
				Log.customer.debug("%s ::: The Supplier object is " + supplier, "config.java.invoicing.sap.CatSAPInvoiceReconciliationEngine" );
                Log.customer.debug("%s ::: The Supplier object is null or invoice number is nullOrEmptyOrBlankString", "config.java.invoicing.sap.CatSAPInvoiceReconciliationEngine");
			//}
            return;
        }

        supplierInvoiceNumber = supplierInvoiceNumber.toUpperCase();

        //if(Log.customer.debugOn)
        //{
            Log.customer.debug("%s ::: The Supplier object is %s", "config.java.invoicing.sap.CatSAPInvoiceReconciliationEngine", supplier.toString());
            Log.customer.debug("%s ::: The Inv # in uppercase is %s", "config.java.invoicing.sap.CatSAPInvoiceReconciliationEngine", supplierInvoiceNumber);
        //}
        AQLQuery tempQuery = AQLQuery.parseQuery(Fmt.S("%s %s %s %s %s '%s'",

        							"SELECT InvoiceReconciliation",
        							"FROM ariba.invoicing.core.InvoiceReconciliation",
        							"WHERE Invoice.Supplier =",
        							AQLScalarExpression.buildLiteral(supplier).toString(),
        							"AND UPPER(Invoice.InvoiceNumber) = ",
        							supplierInvoiceNumber));
        //if (Log.customer.debugOn)
            Log.customer.debug("%s ::: The tempQuery is %s", "config.java.invoicing.sap.CatSAPInvoiceReconciliationEngine", tempQuery.toString());

        AQLOptions options = new AQLOptions(ir.getPartition());
        AQLResultCollection results = Base.getService().executeQuery(tempQuery, options);

        if(results.getSize() > 0)
        {
            boolean isDuplicate = false;
            //if (Log.customer.debugOn)
                Log.customer.debug("%s ::: The size of result set is: " + results.getSize(), "config.java.invoicing.sap.CatSAPInvoiceReconciliationEngine");
            while(results.next())
            {
                BaseId baseId = results.getBaseId(0);
                //if (Log.customer.debugOn)
                //{
                    Log.customer.debug("%s ::: Base ID of result: %s", "config.java.invoicing.sap.CatSAPInvoiceReconciliationEngine", baseId.toString());
                    Log.customer.debug("%s ::: Base ID of ir: %s", "config.java.invoicing.sap.CatSAPInvoiceReconciliationEngine", ir.getBaseId().toString());
                //}

                if(!SystemUtil.equal(baseId, ir.getBaseId()))
                {
                    //if (Log.customer.debugOn)
                        Log.customer.debug("%s ::: Encountered different Base IDs: %s != %s", "config.java.invoicing.sap.CatSAPInvoiceReconciliationEngine", baseId.toString(), ir.getBaseId().toString());
                    isDuplicate = true;
                    break;
                }
            }

            if(isDuplicate)
            {
                Log.customer.debug("%s ::: Setting duplicate flag for %s", "config.java.invoicing.sap.CatSAPInvoiceReconciliationEngine", ir.getUniqueName());
				notDuplicate = new Boolean(false);
			    ir.setDottedFieldValue("IsNotDuplicate", notDuplicate);
				ir.getInvoice().setDottedFieldValue("IsNotDuplicate", notDuplicate);
			}
        }
	}

	/*
	 *  ARajendren, Ariba Inc.,
	 *	Changed the method signature from InvoiceReconciliation to StatementReconciliation
	 */

	protected List getExceptionTypesForHeader(StatementReconciliation sr) {

		//if (Log.customer.debugOn)
			Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: In method for generating exception type for header");
		List exceptions = super.getExceptionTypesForHeader(sr);

		InvoiceReconciliation ir = (InvoiceReconciliation)sr;

		String invPurpose = "";
		String supplierEmailID = "MSC_Help@cat.com";
		String invoiceNumber = "";
		String singNumValue = "";
		Money  totalCost;
		Money  invtotalcost;
		BigDecimal tcAmount = null;
		BigDecimal tcAmount1 = null;
		BigDecimal MATotalCostAmt = null;
		Money totalCostlessInv;
		BigDecimal pototalCostAmount = null;
		BigDecimal poAmountAccpeted = null;
		BigDecimal poAmountInvoiced = null;
		BigDecimal poAmountReconcilied = null;
		BigDecimal invAmountInvoiced = null;
		BigDecimal totalDisputed = null;
		BigDecimal taxPerc = null;
		String stats = "Reject";
		String invliType = "Catalog";
		BigDecimal perc = BigDecimalFormatter.getBigDecimalValue(0.01);
		BigDecimal taxPercent = null;
		BigDecimal totalTaxAmount = null;
		BigDecimal pototalCostAmountWithTax = null;
		BigDecimal taxAmount = null;
		BigDecimal poAmountAccpetedWithTax = null;
		String strTCAmount = "";
		String strPOAmountAccepted = "";
		String strpoAmountInvoiced = "";
		String strpoAmountReconcilied = "";
		String strinvAmountInvoiced = "";
		String irBaseId = ir.getBaseId().toDBString();
		BigDecimal totalOrderIRAmt = null;
		BigDecimal totalRecAmt = null;
		BigDecimal totalMAIRAmt = null;
                Integer CatNum = null;
		BigDecimal Amtleft = null;
		BigDecimal AmtCompareTo = null;
		String InvLinewithouttax = null;
		String InvLineDisputed = null;
		BigDecimal Tolrance = new BigDecimal (0.01);
		BigDecimal Tolrance1 = new BigDecimal (0.1);
		String qryString;
		String qryString2;
		String qryString3;
                String qryString4;
		AQLQuery query;
		AQLQuery query2;
		AQLQuery query3;
		AQLOptions queryOptions;
		AQLQuery query4;
		AQLOptions queryOptions4;
		AQLResultCollection queryResults4;
		AQLResultCollection queryResults;
		AQLResultCollection queryResults2;
		AQLResultCollection queryResults3;



		/*
		if ((ir.getFieldValue("taxCallNotFailed") == null)
				|| ((ir.getFieldValue("taxCallNotFailed") != null) && ((Boolean) ir.getFieldValue("taxCallNotFailed")).booleanValue())) {
				//if (Log.customer.debugOn)
					Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing exception type: %s", TAX_CALC_FAIL);
				exceptions.remove(super.getInvoiceExceptionType(TAX_CALC_FAIL, ir.getPartition()));
				//if (Log.customer.debugOn)
					Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type: %s", TAX_CALC_FAIL);
		}
		*/

		/* Code for Vertex flag check */
					if((ir.getDottedFieldValue("CompanyCode.CallToVertexEnabled")!=null) &&((((String) ir.getDottedFieldValue("CompanyCode.CallToVertexEnabled")).equals("IR"))||(((String) ir
					.getDottedFieldValue("CompanyCode.CallToVertexEnabled")).equals("PIB")))){
//Remove  Tax Exception for Vertex

				Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing over tax exception type: %s", TAX_CALC_FAIL);
				Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: isTaxCalculationFailedFlag: %s", TAX_CALC_FAIL_FLAG_FOR_INVALID_TXAMT);
				if(TAX_CALC_FAIL_FLAG_FOR_INVALID_TXAMT.equals("false"))
						{

						exceptions.remove(super.getInvoiceExceptionType(TAX_CALC_FAIL, ir.getPartition()));

						}




 }


		// ********************* CR189 Order Variance (Vikram J Singh) Starts *********************

//Added by Sandeep to Check TotalCost Less Invoiced on the Invoice
       // ariba.purchasing.core.PurchaseOrder irOrder = (ariba.purchasing.core.PurchaseOrder)ir.getOrder();
		//if (irOrder!= null)

	BaseObject bo = null;
	BaseObject bo1 =null;
        BaseObject bo2 = null;

			if (ir.getOrder() !=null)
							{
								bo1 = ir.getOrder();
								ariba.purchasing.core.PurchaseOrder irOrder = (ariba.purchasing.core.PurchaseOrder)ir.getOrder();
								Log.customer.debug("Order is not null; Get the Order UniqueName - Sandeep" +irOrder );

							}
							Log.customer.debug("Value of bo1 is" +bo1);

							if (ir.getMasterAgreement() != null)
			                {
							bo2 = ir.getMasterAgreement();
							String cOntractbaseId = bo2.getBaseId().toDBString();
							if (bo2 != null)
							{
								ariba.contract.core.Contract irContract = (ariba.contract.core.Contract)ir.getMasterAgreement();
								Log.customer.debug("MA is not null; Get the MA UniqueName - Sandeep" +irContract);
							}
					  }


			               Log.customer.debug("value of bo2 is - Sandeep" +bo2);

					  		if (bo1 != null || bo1 != null && bo2 !=null)
	{
      String oRderbaseId = bo1.getBaseId().toDBString();
				ariba.purchasing.core.PurchaseOrder irOrder = (ariba.purchasing.core.PurchaseOrder)ir.getOrder();
				Log.customer.debug("Order is not null; Get the Order UniqueName - Sandeep" +irOrder );
				Log.customer.debug("Check for Invoice on the IR - Sandeep");
				ariba.invoicing.core.Invoice irInvc = (ariba.invoicing.core.Invoice)(((InvoiceReconciliation)ir).getInvoice());
				tcAmount = new BigDecimal(0.0);
				if (irInvc!=null)
															{

						                   BaseVector irLineItems = ir.getLineItems();
						                InvoiceReconciliationLineItem irLineItem= null;

						 for (int i = 0; i < irLineItems.size(); i++) {
						                                irLineItem = (InvoiceReconciliationLineItem) irLineItems.get(i);
  Log.customer.debug("Getting the line item of the IR");
 if(irLineItem.getLineType() != null)
 {
 Log.customer.debug("LineItem Type is not null on the IR");
                         CatNum = (Integer)irLineItem.getDottedFieldValue("LineType.Category");
                     Log.customer.debug("The Category of the Line items is " +CatNum.intValue());
                        if ((CatNum.intValue()== 1))

         {
              Log.customer.debug("LineItem Type is catalog");
                 if(irLineItem.getAmount() !=null)
                 {
              Log.customer.debug ("Getting Line Item amt for Type only catalog");
              Log.customer.debug ("Amt in Base Currency" +irLineItem.getDottedFieldValue("Amount.ApproxAmountInBaseCurrency"));
              tcAmount1= (BigDecimal) irLineItem.getDottedFieldValue("Amount.ApproxAmountInBaseCurrency");
              Log.customer.debug ("Getting the Line Amt- tcAmt1" +tcAmount1);
               }

               else {
               tcAmount1=new BigDecimal(0.0);
               Log.customer.debug("Amount of the Line Items is null- Sandeep" +tcAmount1);
               }

         tcAmount = tcAmount.add(tcAmount1);
         }
        }
 }
  Log.customer.debug("%s::Total Cost Amount Less Tax,Shipping,Charge on Inv -Sandeep:%s",ClassName,tcAmount);

										int signnumInt = tcAmount.signum();
										singNumValue  = IntegerFormatter.getStringValue(signnumInt);
															      Log.customer.debug("%s::String value of sign num total cost:%s",ClassName,singNumValue);
			      }




							Log.customer.debug("for Invoices which has Order on the Invoice Reonciliation");
							qryString = "select SUM(Li.Amount.ApproxAmountInBaseCurrency) from ariba.invoicing.core.InvoiceReconciliation "
						        +  " JOIN ariba.invoicing.core.InvoiceReconciliationLineItem as Li using InvoiceReconciliation.LineItems "
						        +  " JOIN ariba.procure.core.ProcureLineType as Ptype using Li.LineType "
							+  " where InvoiceReconciliation.StatusString  not like ('%"+stats+"%') "
						        +  " and InvoiceReconciliation <> baseid ('"+irBaseId+"') "
							+  " and InvoiceReconciliation.Order = baseid ('"+oRderbaseId+"') "
							+  " and Ptype.UniqueName like ('%"+invliType+"%') ";


					 qryString2 = "select SUM(recli.AmountAccepted.ApproxAmountInBaseCurrency) from ariba.purchasing.core.PurchaseOrder as PurchaseOrder "
							+" JOIN ariba.receiving.core.Receipt as rec using PurchaseOrder.Receipts "
							+" JOIN ariba.receiving.core.ReceiptItem as recli using rec.ReceiptItems "
							+" where PurchaseOrder = baseid ('"+oRderbaseId+"') "
							+" and rec.StatusString not in ('Rejected','Composing') ";

							qryString3 = "select SUM(Li.Amount.ApproxAmountInBaseCurrency) from ariba.invoicing.core.InvoiceReconciliation "
						        +  " JOIN ariba.invoicing.core.InvoiceReconciliationLineItem as Li using InvoiceReconciliation.LineItems "
						        +  " JOIN ariba.procure.core.ProcureLineType as Ptype using Li.LineType "
						        +  " JOIN ariba.invoicing.core.InvoiceException as Exc using Li.Exceptions "
							+  " where InvoiceReconciliation.StatusString  not like ('%"+stats+"%') "
						        +  " and InvoiceReconciliation <> baseid ('"+irBaseId+"') "
							+  " and InvoiceReconciliation.Order = baseid ('"+oRderbaseId+"') "
							+  " and Exc.State == 4 "
							+  " and Ptype.UniqueName like ('%"+invliType+"%')";

						    Log.customer.debug(" qryString= " + qryString);
						    Log.customer.debug(" queryString3 =" +qryString3);
							Log.customer.debug(" qryString2= " + qryString2);
						    query = AQLQuery.parseQuery(qryString);
							query2 = AQLQuery.parseQuery(qryString2);
							query3 = AQLQuery.parseQuery(qryString3);
						    Log.customer.debug(" *** query= " + query);
							queryOptions = new AQLOptions(ir.getPartition());
					        queryResults = Base.getService().executeQuery(query,queryOptions);
							Log.customer.debug(" *** query2= " + query2);
					        queryResults2 = Base.getService().executeQuery(query2,queryOptions);
					        Log.customer.debug(" *** query3= " + query3);
					        queryResults3 = Base.getService().executeQuery(query3,queryOptions);
                                                Log.customer.debug("Done Executing the Query");
					        if (queryResults3.next())
				          {
                                                     Log.customer.debug("Checking the Result in Query 3");
							  totalDisputed = queryResults3.getBigDecimal(0);
							  Log.customer.debug("Query 3 *** has result take the first value");

							  if (totalDisputed == null)
							  {
								  totalDisputed = new BigDecimal(0.0);
								 Log.customer.debug("Query 3 *** has null result taking value as 0.00");
							 }
						}

							if (queryResults.next())
							{
								BigDecimal totalOrderIRAmt1 = null;
								totalOrderIRAmt1 = queryResults.getBigDecimal(0);
								Log.customer.debug("Query 1 *** has result take the first value");

								if (totalOrderIRAmt1 == null)
								{
									totalOrderIRAmt1 = new BigDecimal(0.0);
									Log.customer.debug("Query 1 *** has result as null set as 0.00USD ");
								}
								totalOrderIRAmt = totalOrderIRAmt1.subtract(totalDisputed);
								Log.customer.debug("Total Amt of the invoice to be compared is" +totalOrderIRAmt);
							}
							if (queryResults2.next())
							{
								totalRecAmt = queryResults2.getBigDecimal(0);
								Log.customer.debug("Query 2 *** getting the total Received amt from Receipt query ***" +totalRecAmt);
								if (totalRecAmt == null )
								{
									totalRecAmt = new BigDecimal(0.0);
									Log.customer.debug("Query 2 *** No Receipts Processed hence total receipt amt is 0.00 ***");
								}
				            }





  Money orderTotalCostobj = irOrder.getTotalCost();
    if (orderTotalCostobj != null)
    {
    pototalCostAmount = (BigDecimal)irOrder.getDottedFieldValue("TotalCost.ApproxAmountInBaseCurrency");
    Log.customer.debug("Getting Amt of the Order in BaseCurrency" +pototalCostAmount);
   }

  if (totalRecAmt.compareTo(pototalCostAmount) <=0)
  {
  	Log.customer.debug("***Check AmtCompareTo over***");
  	AmtCompareTo = pototalCostAmount;
  	Log.customer.debug("***AmtCompareTo is same as POtotal ***" +AmtCompareTo);
  }
  else
  {
  	AmtCompareTo = totalRecAmt;
  	Log.customer.debug("***AmtCompareTo is same as ReceiptTotal***" +AmtCompareTo);
  }
  BigDecimal Amtleft1 = null;
  Amtleft1 = AmtCompareTo.subtract(totalOrderIRAmt);
  Log.customer.debug("Amt left over to compare without tolreance" +Amtleft1);
  if (ir.getDottedFieldValue("CompanyCode.UniqueName")!=null)
  {
    String CCode = (String)ir.getDottedFieldValue("CompanyCode.SAPSource");

	  Log.customer.debug("CompanyCode" +CCode);
	  if (CCode !=null)
  {
	  Log.customer.debug("CompanyCode is not null");
	  if (CCode.equals("MACH1"))
	  {
		  Log.customer.debug("Add Tolreance for CC 1000 and 1885" +Amtleft1);
      BigDecimal Tr = (BigDecimal) (Amtleft1.multiply(Tolrance));
      Log.customer.debug("Amt of Tolerance to be added" +Tr);
		  Amtleft = Amtleft1.add(Tr);
	  }
	  else
	  {
		  BigDecimal Tr1 = (BigDecimal) (Amtleft1.multiply(Tolrance1));
		  				                    Log.customer.debug("Amt of Tolerance to be added" +Tr1);
				                    Amtleft = Amtleft1.add(Tr1);
		  Log.customer.debug("Add Tolreance for Company Code not MACH1");
		   Amtleft = Amtleft1.add(Tr1);
    }
 }
}

Log.customer.debug("***AmtLeft ***" +Amtleft);


            /* Check for Close Order Variance

			//if (Log.customer.debugOn)
				Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: CloseOrder Value IS: %s...", (Boolean)ir.getDottedFieldValue("Order.CloseOrder") );
			if ( ( ir.getDottedFieldValue("Order.CloseOrder") != null ) && ( ((Boolean)ir.getDottedFieldValue("Order.CloseOrder")).booleanValue() ) && !singNumValue.equals("-1") )
			{
				//if (Log.customer.debugOn)
					Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: CloseOrder Value IS: %s... Hence NOT removing the %s exception", (Boolean)ir.getDottedFieldValue("Order.CloseOrder"), CLOSE_ORDER);
			}

			else
			{
				//if (Log.customer.debugOn)
					Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing exception type: %s", CLOSE_ORDER);
				exceptions.remove(super.getInvoiceExceptionType(CLOSE_ORDER, ir.getPartition()));

				//if (Log.customer.debugOn)
					Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type: %s", CLOSE_ORDER);
			}
            */
                     //  ***** WI 295 Starts ********
			/* Check for Close Order Variance based on "Closed" field
			 * If Closed = 1 , Order is Open
			 * If Closed = 4 , Order is closed for invoicing and if Closed = 5 , Order is closed for all.
			 */

			 if ( irOrder != null) {
			 Integer closeOrder = (Integer) irOrder.getFieldValue("Closed");
			 int closeState = closeOrder.intValue();
			 Log.customer.debug("%s::: CloseOrder Value IS: %s...",ClassName,closeOrder);
			 if ((closeState == 4 || closeState == 5) && !singNumValue.equals("-1"))
			 {
			    Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: CloseOrder Value IS: %s... Hence NOT removing the %s exception", (Boolean)ir.getDottedFieldValue("Order.CloseOrder"), CLOSE_ORDER);
		     }
		     else
		     {
				    Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing exception type: %s", CLOSE_ORDER);
				   	exceptions.remove(super.getInvoiceExceptionType(CLOSE_ORDER, ir.getPartition()));
				    Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type: %s", CLOSE_ORDER);
			  }

		  }
                  // ******* WI 295 Ends *********


			//Check for Cancel Order Variance

			Integer orderedState = (Integer)ir.getDottedFieldValue("Order.OrderedState");
                        int orderStatetemp = orderedState.intValue();

			//if (Log.customer.debugOn)
			        Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: CancelledOrder Code");
			//if (( ir.getDottedFieldValue("Order.OrderedState") != null ) && (orderStatetemp == 16))
			if (( ir.getDottedFieldValue("Order.OrderedState") != null ) && (orderStatetemp == 16) && !singNumValue.equals("-1"))
			{
				//if (Log.customer.debugOn)
			    	Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: CancelledOrder Value IS: %s... Hence NOT removing the %s exception", ir.getDottedFieldValue("Order.OrderedState"), CANCELLED_ORDER);
			}
			else
			{
			    //if (Log.customer.debugOn)
			    	Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing exception type: %s", CANCELLED_ORDER);
			    exceptions.remove(super.getInvoiceExceptionType(CANCELLED_ORDER, ir.getPartition()));
			    //if (Log.customer.debugOn)
			    	Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type: %s", CANCELLED_ORDER);
			}



             //if (Log.customer.debugOn)
				Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Entering Transaction Completed Variance Code" );
			   	Log.customer.debug("Comparing the IR and the Amt left total FINAL ****");
				Log.customer.debug("IR Amt to be compared" +tcAmount);
			    Log.customer.debug("Amt left that is invoicable" +Amtleft);
				if (tcAmount.compareTo(Amtleft) > 0 && !singNumValue.equals("-1"))
				{
					//if (Log.customer.debugOn)
				Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Transaction Completed... Hence NOT removing the %s exception", TRANS_COMP_VAR);

               }

			else
			{
				Log.customer.debug("DO is still INVOICABLE *** REMOVE TRANSCATION VARAINCE ***");
			 	//if (Log.customer.debugOn)
			    	Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing exception type: %s", TRANS_COMP_VAR);
			        exceptions.remove(super.getInvoiceExceptionType(TRANS_COMP_VAR, ir.getPartition()));
			        //if (Log.customer.debugOn)
			        	Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type: %s", TRANS_COMP_VAR);
			}

			// Zero Amount Variance Code


			ariba.invoicing.core.Invoice irInvo = (ariba.invoicing.core.Invoice)(((InvoiceReconciliation)ir).getInvoice());
			if (irInvo!=null){
				invtotalcost = (Money)irInvo.getFieldValue("TotalCost");

				if (invtotalcost!=null) {

					invAmountInvoiced = (BigDecimal)invtotalcost.getAmount();
					strinvAmountInvoiced = BigDecimalFormatter.getStringValue(invAmountInvoiced);
					Log.customer.debug("%s::Amount Invoiced :%s",ClassName,strinvAmountInvoiced);
				}



				//if (Log.customer.debugOn)
					Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Zero Amount Variance Code");
				if ( (invtotalcost!=null) && (strinvAmountInvoiced.equals("0")) )
				{
					//if (Log.customer.debugOn)
			   		Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Amount Invoiced Value IS: %s... Hence NOT removing the %s exception", strinvAmountInvoiced, ZERO_AMOUNT);
				}

				else
				{
					//if (Log.customer.debugOn)
			   		Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing exception type: %s", ZERO_AMOUNT);
					exceptions.remove(super.getInvoiceExceptionType(ZERO_AMOUNT, ir.getPartition()));
					//if (Log.customer.debugOn)
						Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type: %s", ZERO_AMOUNT);
				}
			}
	  }


		// ********************* CR189 Order Variance (Vikram J Singh) Ends *********************

		// ********************* CR189 Contract Variance (Vikram J Singh) Starts *********************
		else {
			if(ir.getMasterAgreement() !=null)
			  {
			ariba.contract.core.Contract irContract = (ariba.contract.core.Contract)ir.getMasterAgreement();

			if (irContract != null)
			{
				String MAbaseId = bo2.getBaseId().toDBString();
						Log.customer.debug("Base Id of MA is" + MAbaseId);
				ariba.invoicing.core.Invoice irInv = (ariba.invoicing.core.Invoice)(((InvoiceReconciliation)ir).getInvoice());
				tcAmount = new BigDecimal(0.0);
				if (irInv!=null){



                   BaseVector irLineItems = ir.getLineItems();
                InvoiceReconciliationLineItem irLineItem= null;

 for (int i = 0; i < irLineItems.size(); i++) {
                                irLineItem = (InvoiceReconciliationLineItem) irLineItems.get(i);
                                Log.customer.debug("Getting the line item of the IR");
                                if(irLineItem.getLineType() != null)
                                {
                                Log.customer.debug("LineItem Type is not null on the IR");
CatNum = (Integer)irLineItem.getDottedFieldValue("LineType.Category");
 Log.customer.debug("The Category of the Line items is " +CatNum.intValue() );
  if ((CatNum.intValue()== 1))

                                        {
                                             Log.customer.debug("LineItem Type is catalog");
                                                if(irLineItem.getAmount() !=null)
                    {
                 Log.customer.debug ("Getting Line Item amt for Type only catalog");
                 Log.customer.debug ("Getting Amt in base Currency" +irLineItem.getDottedFieldValue("Amount.ApproxAmountInBaseCurrency"));
                 tcAmount1= (BigDecimal) irLineItem.getDottedFieldValue("Amount.ApproxAmountInBaseCurrency");
                 Log.customer.debug("Getting the Line Amt TcAmt1" +tcAmount1);

                  }

                  else {
                  tcAmount1=new BigDecimal(0.0);
                  Log.customer.debug("Amount of the Line Items is null- Sandeep" +tcAmount1);
                  }

            tcAmount = tcAmount.add(tcAmount1);
            }
                          }
								 }
                                 Log.customer.debug("%s::Total Cost Amount Less Tax,Shipping,Charge on Inv -Sandeep:%s",ClassName,tcAmount);

				int signnumInt = tcAmount.signum();
				singNumValue  = IntegerFormatter.getStringValue(signnumInt);
			      Log.customer.debug("%s::String value of sign num total cost:%s",ClassName,singNumValue);
			      }









		        int contractState = irContract.getMAState();
				//int contractStatetemp = contractState.intValue();
		        //Check for Close Contract Variance
				//if (Log.customer.debugOn)
		                Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: CloseContract Code");
		        if (( ir.getDottedFieldValue("MasterAgreement.MAState") != null ) && (contractState == 16) && !singNumValue.equals("-1") && ! irContract.getIsReceivable())
				{
		         	//if (Log.customer.debugOn)
		            	Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: MAState Value IS: %s... Hence NOT removing the %s exception", ir.getDottedFieldValue("MasterAgreement.MAState"), CLOSED_CONTRACT);

		        }


  else
  {
                                                                         //if (Log.customer.debugOn)
                                                                        Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing exception type: %s", CLOSED_CONTRACT);
                                                                                        exceptions.remove(super.getInvoiceExceptionType(CLOSED_CONTRACT, ir.getPartition()));
                                                                                        //if (Log.customer.debugOn)
                                                           Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type: %s", CLOSED_CONTRACT);
                                                                                }

if (irContract.getIsInvoiceable() && !singNumValue.equals("-1") && irContract.getMaxAmount()!= null )
		                    	{


										Money MATotalCostobj = (Money)irContract.getFieldValue("MaxAmount");
										if (MATotalCostobj !=null)
										{
									MATotalCostAmt =(BigDecimal)MATotalCostobj.getFieldValue("ApproxAmountInBaseCurrency");
										Log.customer.debug("MA Total Cost In BaseCurrency ***" +MATotalCostAmt);
							              }

							        qryString = "select SUM(Li.Amount.ApproxAmountInBaseCurrency) from ariba.invoicing.core.InvoiceReconciliation "
				                                       +  " JOIN ariba.invoicing.core.InvoiceReconciliationLineItem as Li using InvoiceReconciliation.LineItems "
								       +  " JOIN ariba.procure.core.ProcureLineType as Ptype using Li.LineType "
								       +  " where InvoiceReconciliation.StatusString  not like ('%"+stats+"%') "
					                               +  " and InvoiceReconciliation <> baseid ('"+irBaseId+"') "
								       +  " and InvoiceReconciliation.MasterAgreement = baseid ('"+MAbaseId+"') "
					                              	+  " and Ptype.UniqueName like ('%"+invliType+"%')";

                                   qryString3 = "select SUM(Li.Amount.ApproxAmountInBaseCurrency) from ariba.invoicing.core.InvoiceReconciliation "
						        +  " JOIN ariba.invoicing.core.InvoiceReconciliationLineItem as Li using InvoiceReconciliation.LineItems "
						        +  " JOIN ariba.procure.core.ProcureLineType as Ptype using Li.LineType "
						        +  " JOIN ariba.invoicing.core.InvoiceException as Exc using Li.Exceptions "
						    	+  " where InvoiceReconciliation.StatusString  not like ('%"+stats+"%') "
						        +  " and InvoiceReconciliation <> baseid ('"+irBaseId+"') "
							    +  " and InvoiceReconciliation.MasterAgreement = baseid ('"+MAbaseId+"') "
							    +  " and Exc.State == 4 "
							    +  " and Ptype.UniqueName like ('%"+invliType+"%')";




									Log.customer.debug(" qryString= " + qryString);
									Log.customer.debug(" qryString3= " + qryString3);
									query = AQLQuery.parseQuery(qryString);
									query3 = AQLQuery.parseQuery(qryString3);
									Log.customer.debug(" *** query= " + query);
										Log.customer.debug(" *** query= " + query3);
									queryOptions = new AQLOptions(ir.getPartition());
									queryResults = Base.getService().executeQuery(query,queryOptions);
									queryResults3 = Base.getService().executeQuery(query3,queryOptions);

                                       if (queryResults3.next())
									  				          {
									  							  totalDisputed = queryResults3.getBigDecimal(0);
									  							  Log.customer.debug("Query 3 *** has result take the first value");

									  							  if (totalDisputed == null)
									  							  {
									  								  totalDisputed = new BigDecimal(0.0);
									  								 Log.customer.debug("Query 3 *** has null result taking value as 0.00");
									  							 }
						                                     }


	                                                     if (queryResults.next())
														{
													BigDecimal totalMAIRAmt1 = null;
									 				totalMAIRAmt1 = queryResults.getBigDecimal(0);
													Log.customer.debug("Query 1 *** has result take the first value");

													if (totalMAIRAmt1 == null)
													{
														totalMAIRAmt1 = new BigDecimal(0.0);
														Log.customer.debug("Query 1 *** has result as null set as 0.00USD ");
													}
													totalMAIRAmt = totalMAIRAmt1.subtract(totalDisputed);
									          Log.customer.debug("Total Amt of the invoice to be compared is" +totalMAIRAmt);

											 }

		                          if (irContract.getIsReceivable() && contractState !=16)
		                          {


			            qryString2 ="select SUM(recli.AmountAccepted.ApproxAmountInBaseCurrency) from ariba.contract.core.Contract as MasterAgreement"
											+" JOIN ariba.receiving.core.Receipt as rec using MasterAgreement.Receipts "
											+" JOIN ariba.receiving.core.ReceiptItem as recli using rec.ReceiptItems "
											+" where MasterAgreement = baseid ('"+MAbaseId+"') "
											+" and rec.StatusString not in ('Rejected','Composing') ";


											Log.customer.debug(" qryString2= " + qryString2);
										   	query2 = AQLQuery.parseQuery(qryString2);
											queryOptions = new AQLOptions(ir.getPartition());
									        Log.customer.debug(" *** query2= " + query2);
									        queryResults2 = Base.getService().executeQuery(query2,queryOptions);



							 if (queryResults2.next())
							 				{
							 					totalRecAmt = queryResults2.getBigDecimal(0);
							 					Log.customer.debug("Query MA 2 *** getting the total Received amt from Receipt query ***" +totalRecAmt);
							 					if (totalRecAmt == null )
							 					{
							 						totalRecAmt = new BigDecimal(0.0);
							 						Log.customer.debug("Query MA 2 *** No Receipts Processed hence total receipt amt is 0.00 ***");
							 					}
											}




							if (totalRecAmt.compareTo(MATotalCostAmt) <=0)
							{
								Log.customer.debug("***Check AmtCompareTo over***");
								AmtCompareTo = MATotalCostAmt;
								Log.customer.debug("***AmtCompareTo is same as MAtotal ***" +AmtCompareTo);
							}
							else
							{
								AmtCompareTo = totalRecAmt;
								Log.customer.debug("***AmtCompareTo is same as ReceiptTotal***" +AmtCompareTo);
							}
					}
					else
					{
				Log.customer.debug("Contract is not Receivable");
					      AmtCompareTo = MATotalCostAmt;

				}

					BigDecimal Amtleft1 = null;
					  Amtleft1 = AmtCompareTo.subtract(totalMAIRAmt);
					  Log.customer.debug("Amt left over to compare without tolreance" +Amtleft1);
					  if (ir.getDottedFieldValue("CompanyCode.UniqueName") !=null)
					  {
						  String CCode =(String)ir.getDottedFieldValue("CompanyCode.SAPSource");
						  Log.customer.debug("CompanyCode" +CCode);
						  if (CCode !=null)
					  {
						  Log.customer.debug("CompanyCode is not null");
						  if (CCode.equals("MACH1"))
						  {
							  Log.customer.debug("Add Tolreance for MACH1"  +Amtleft1);


							        BigDecimal Tr = (BigDecimal) (Amtleft1.multiply(Tolrance));
							        Log.customer.debug("Amt of Tolerance to be added" +Tr);
		                              Amtleft = Amtleft1.add(Tr);
						  }
						  else
						  {
							  Log.customer.debug("Add Tolreance for Company Code not MACH1");
							   BigDecimal Tr1 = (BigDecimal) (Amtleft1.multiply(Tolrance1));
							   		  		  				                    Log.customer.debug("Amt of Tolerance to be added" +Tr1);
							   		  				                    Amtleft = Amtleft1.add(Tr1);
							   		  		  Log.customer.debug("Add Tolreance for Company Code not MACH1");
		                                        Amtleft = Amtleft1.add(Tr1);
		                               Log.customer.debug("Final Amt left to be compare" +Amtleft);

					    }
					 }
}


					Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Enter Else if of close contract Variance");

					if (tcAmount.compareTo(Amtleft) > 0 && !singNumValue.equals("-1"))
												{

										        	Log.customer.debug("%s:: Not removing Contract Close Variance as is fully received and invoiced",ClassName);

										        }
												else
					                    		{
					                            	//if (Log.customer.debugOn)
									Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing exception type: %s",MA_NOT_INVOICING );
					                            	exceptions.remove(super.getInvoiceExceptionType(MA_NOT_INVOICING, ir.getPartition()));
					                            	//if (Log.customer.debugOn)
								    Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type: %s", MA_NOT_INVOICING);
												}

                    	}

										// If Credit Invoice, dont reject IR with 'Contract Not Invoicing' exception
										if(singNumValue.equals("-1"))
										{
											//if (Log.customer.debugOn)
												Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing exception type: %s", CLOSED_CONTRACT);
											exceptions.remove(super.getInvoiceExceptionType(CLOSED_CONTRACT, ir.getPartition()));
										}

									}

							 	// ********************* CR189 MA Ends (Vikram J Singh) Ends *********************
		        }
		   }
		     return exceptions;

	}

	/*
 	 *  ARajendren, Ariba Inc.,
	 *	Changed the method signature from InvoiceReconciliationLineItem to StatementReconciliationLineItem
	 */

	protected List getExceptionTypesForLine (StatementReconciliationLineItem srli)
	{
		//if (Log.customer.debugOn)

			Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: In method for generating exception type for line");

		List exceptions = super.getExceptionTypesForLine(srli);

		InvoiceReconciliationLineItem irli =(InvoiceReconciliationLineItem)srli;

		ProcureLineItem pli = (ProcureLineItem)getProcureLineItem((InvoiceReconciliationLineItem)irli);
		if(CatSAPAdditionalChargeLineItem.isAdditionalCharge(pli) && (CatSAPValidReferenceLineNumber.validReferenceLine(pli) == 0 || CatSAPValidReferenceLineNumber.validReferenceLine(pli) == 5))
        {
			//if (Log.customer.debugOn)
				Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing exception type: %s", PO_RCVD_Q_V);
			exceptions.remove(super.getInvoiceExceptionType(PO_RCVD_Q_V, irli.getPartition()));
			//if (Log.customer.debugOn)
				Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type: %s", PO_RCVD_Q_V);
			//if (Log.customer.debugOn)
				Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing exception type: %s", MA_RCVD_Q_V);
			exceptions.remove(super.getInvoiceExceptionType(MA_RCVD_Q_V, irli.getPartition()));
			//if (Log.customer.debugOn)
				Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type: %s", MA_RCVD_Q_V);
        }

	//Vertex Change - included check for CompanyCode.CallToVertexEnabled against values IR and PIB as vertex should not have a
	//Tax manager before its being called

		InvoiceReconciliation ir = (InvoiceReconciliation)irli.getLineItemCollection();
		if ((irli.getDottedFieldValue("LineItemCollection.taxCallNotFailed") == null)||(( ir.getDottedFieldValue("CompanyCode.CallToVertexEnabled")!=null) &&((((String) ir
					.getDottedFieldValue("CompanyCode.CallToVertexEnabled")).equals("IR"))||(((String) ir
					.getDottedFieldValue("CompanyCode.CallToVertexEnabled")).equals("PIB"))))
				|| ((irli.getDottedFieldValue("LineItemCollection.taxCallNotFailed") != null)

					&& ((Boolean) irli.getDottedFieldValue("LineItemCollection.taxCallNotFailed")).booleanValue())) {

				//if (Log.customer.debugOn)

					Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing exception type: %s", OVER_TAX);

				exceptions.remove(super.getInvoiceExceptionType(OVER_TAX, irli.getPartition()));

				//if (Log.customer.debugOn)

					Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type: %s", OVER_TAX);

			}
			if((ir.getDottedFieldValue("CompanyCode.CallToVertexEnabled")!=null)&&((((String) ir.getDottedFieldValue("CompanyCode.CallToVertexEnabled")).equals("IR"))||(((String) ir
					.getDottedFieldValue("CompanyCode.CallToVertexEnabled")).equals("PIB")))){
//Remove Over Tax Exception for Vertex
				//Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing over tax exception type: %s", OVER_TAX);

			//	exceptions.remove(super.getInvoiceExceptionType(OVER_TAX, irli.getPartition()));

				//Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type: %s", OVER_TAX);

//Remove CAT Tax Calculation Failed Exception for Vertex
				Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing over tax exception type: %s", TAX_CALC_FAIL);

				//exceptions.remove(super.getInvoiceExceptionType(TAX_CALC_FAIL, irli.getPartition()));




				//Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type: %s", TAX_CALC_FAIL);
//Remove  Tax Calculation Failed Exception for Vertex
				Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing over tax exception type: %s", GENERIC_TAX_CALC_FAIL);

				exceptions.remove(super.getInvoiceExceptionType(GENERIC_TAX_CALC_FAIL, irli.getPartition()));

				Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type: %s", GENERIC_TAX_CALC_FAIL);




			}

	    //Removing Quantity exceptions for credit lines
		if (irli.getAmount().isNegative()) {
			for (int i = 0; i < receiptExcTypes.length; i++) {
				exceptions.remove(getInvoiceExceptionType(receiptExcTypes[i], irli.getPartition()));
			}
		}


		//The unitprice check for tolerance

		if (unitPriceWithinTolerence((InvoiceReconciliationLineItem)irli)){

			Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing exception type: %s", PO_PRC_V);

			exceptions.remove(super.getInvoiceExceptionType(PO_PRC_V, irli.getPartition()));

			Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type: %s", PO_PRC_V);



			Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing exception type: %s", PO_CATPRC_V);

			exceptions.remove(super.getInvoiceExceptionType(PO_CATPRC_V, irli.getPartition()));

			Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type: %s", PO_CATPRC_V);


			/*
			Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing exception type: %s", MA_NC_PRC_V);

			exceptions.remove(super.getInvoiceExceptionType(MA_NC_PRC_V, irli.getPartition()));

			Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type: %s", MA_NC_PRC_V);
			*/


			Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing exception type: %s", MA_CAT_PRC_V);

			exceptions.remove(super.getInvoiceExceptionType(MA_CAT_PRC_V, irli.getPartition()));

			Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type: %s", MA_CAT_PRC_V);

		}

		//Santanu : As per new Requirement on the invalid accounting distribution exception
		if(!getAccountDistExcpRequired((InvoiceReconciliationLineItem)irli)){
			exceptions.remove(super.getInvoiceExceptionType(CAT_INVALID_ACCTNG, irli.getPartition()));
			Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type: %s", CAT_INVALID_ACCTNG);
		}
		//Santanu : As per new Requirement on the invalid accounting distribution exception
       //Start: Mach1 R5.5 (FRD2.5/TD2.5)
		 String partitionName = irli.getPartition().getName();
		 Log.customer.debug("CatSAPInvoiceReconciliationEngine :**********partitionName***********: %s", partitionName);
       Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing exception type:additionalLinesPresent ULI ");
       if (irli.getFieldValue("OrderLineItem") == null && irli.getFieldValue("MALineItem") == null && partitionName == "SAP") {
            Money itemAmt = irli.getAmount();
            BigDecimal itemAmtValue = (BigDecimal)itemAmt.getAmount();
		    BigDecimal value = new BigDecimal(3);
		    Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB 6 ASN itemAmt exception=> " + itemAmt);
		    Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB 6 ASN itemAmtValue exception=> " + itemAmtValue);
		   	Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB 6 ASN value exception => " + value);
		   	Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB 6 ASN ASN itemAmt.compareTo(value= exception> " + itemAmtValue.compareTo(value));
	        if( itemAmtValue.compareTo(value) < 0) {
	   		Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing exception type:ULI %s", UNMATCHED_LINE);
	   		exceptions.remove(super.getInvoiceExceptionType(UNMATCHED_LINE, irli.getPartition()));
	   		Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type: %s", UNMATCHED_LINE);
	   		exceptions.remove(super.getInvoiceExceptionType(CAT_INVALID_ACCTNG, irli.getPartition()));
			Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type for ULI: %s", CAT_INVALID_ACCTNG);
	   		exceptions.remove(super.getInvoiceExceptionType(QUANTITY, irli.getPartition()));
			Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type for ULI: %s", QUANTITY);
	   		exceptions.remove(super.getInvoiceExceptionType(RECEIVED_QUANTITY, irli.getPartition()));
			Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type for ULI: %s", RECEIVED_QUANTITY);
			}
	    }
           Invoice invoice = ir.getInvoice();
           Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB additionaleFormLinesPresent=>5 " + additionaleFormLinesPresent);
        //End: Mach1 R5.5 (FRD2.5/TD2.5)
		// Logic to check credit invoices against Line level exceptions

		Money  totalCost;
		BigDecimal tcAmount;
		String singNumValue = "";

		ariba.contract.core.Contract irContract = (ariba.contract.core.Contract)irli.getMasterAgreement();

		if (irContract != null)
		{
			ariba.invoicing.core.Invoice irInv = (ariba.invoicing.core.Invoice)(((InvoiceReconciliationLineItem)irli).getInvoice());
			if (irInv!=null){

				// Getting the Invoice Total Cost amount's Sign
				totalCost = (Money)irInv.getFieldValue("TotalCost");
				if (totalCost!= null) {

				tcAmount = (BigDecimal)totalCost.getAmount();
				Log.customer.debug("%s::Total Cost Amount:%s",ClassName,tcAmount);

				int signnumInt = tcAmount.signum();
				singNumValue  = IntegerFormatter.getStringValue(signnumInt);
				Log.customer.debug("%s::String value of sign num total cost:%s",ClassName,singNumValue);

				}

			}
			if(singNumValue.equals("-1"))
			{
				Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing exception type: %s", MA_LINE_DATE_VAR);

				exceptions.remove(super.getInvoiceExceptionType(MA_LINE_DATE_VAR, irli.getPartition()));

				Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type: %s", MA_LINE_DATE_VAR);

			}

		}


		Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Returning Line Exceptions : " + exceptions);

	    return exceptions;
    }

	private boolean getAccountDistExcpRequired(InvoiceReconciliationLineItem irli) {
		ProcureLineType procureLineType = (ProcureLineType)irli.getLineType();
		if(procureLineType==null || procureLineType.getCategory()!=ProcureLineType.LineItemCategory){
			return false;
		}
		//  ***** Vikram: WI 320 Starts ********
		// Commented out below 'if' block as the return statement was blocking acctg validation. The below block did not carry relevance
		/*if(irli.getMasterAgreement()==null || irli.getMasterAgreement().getReleaseType()!= ContractCoreApprovable.ReleaseTypeNone){
			return false;
		}*/
		if(!CATSAPUtils.validateIRLineAccounting(irli)){
			return true;
		}
		//  ***** Vikram: WI 320 Ends ********
		return false;
	}

	protected static InvoiceExceptionType getInvoiceExceptionType (String uName, Partition p)
	{
		return InvoiceExceptionType.lookupByUniqueName(uName, p);
	}

	private void setHeaderTaxDetail(Approvable approvable) {
		InvoiceReconciliation ir = (InvoiceReconciliation) approvable;
		if (ir.getTaxDetails().size() == 1) {
			ir.setDottedFieldValue("HeaderTaxDetail", ir.getTaxDetails().get(0));
		}
	}

	protected List generateExceptions(BaseObject parent, List typesToValidate) {

		List exceptions = super.generateExceptions(parent, typesToValidate);



		if (parent instanceof InvoiceReconciliation) {

				exceptions = generateHeaderExceptions((InvoiceReconciliation) parent, typesToValidate, exceptions);

		}

		else {

				exceptions = generateLineExceptions((InvoiceReconciliationLineItem) parent, typesToValidate, exceptions);

		}

		//if (Log.customer.debugOn)
		/*

				Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: In method for generating exceptions");

		for (int i = 0; i < exceptions.size(); i++) {

				Log.customer.debug(

						"CatSAPInvoiceReconciliationEngine ::: Exception generated is %s",

						((InvoiceException) exceptions.get(i)).getType().getUniqueName());

		}
		*/

		return exceptions;

	}

	/*

	 *	Generate any custom Header Exceptions

	 */

	private List generateHeaderExceptions(InvoiceReconciliation ir, List typesToValidate, List exceptions) {

		return exceptions;
	}

    /**
        S. Sato - Overridden to cleanup duplicate tax calculation exceptions which
        show up because of the IR engine being invoked multiple times.

        @param oldExceptions the original exceptions which will contain
                             all the merged exceptions at return time
                             (must be non-null.)
        @param newExceptions the new exceptions being merged into the old
                             (must be non-null.)

        @return a boolean indicating whether any exceptions were kept.
    */
    protected boolean mergeExceptions (List oldExceptions,
                                       List newExceptions)
    {
        boolean mergeExceptions =
            super.mergeExceptions(oldExceptions, newExceptions);

            // fix for issue where multiple tax calculation failed exceptions
            // show up (most of them being in 'Cleared' status
        Log.customer.debug(
                "CatSAPInvoiceReconciliationEngine ::: " +
                "Custom - cleaning up header exceptions");
				Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: inside mergeException-Test1");
        InvoiceException finalTaxException = null;
        InvoiceException finalClearedTaxException = null;
        List exceptionsToBeRemoved = ListUtil.list();
        for (int i = 0; i < oldExceptions.size(); i++) {
            InvoiceException ie = (InvoiceException) oldExceptions.get(i);
            String type = (String) ie.getDottedFieldValue("Type.UniqueName");
			 Log.customer.debug(":: :: Exception Type :: :: "+type);

                // only process tax calculation failed exceptions
            if (type != null && TAX_CALC_FAIL.equals(type))
				{
				Log.customer.debug(":: :: Exception to be removed :: :: "+ie);
				exceptionsToBeRemoved.add(ie);
                int state = ie.getState();
                if ((InvoiceException.Unreconciled == state ||
                        InvoiceException.Accepted == state ||
                         InvoiceException.Disputed == state) ) {
					Log.customer.debug(":: :: Exception that are final :: :: "+ie);
                    finalTaxException = ie;
                }
                else {

                        // cleared exceptions
					Log.customer.debug(":: :: Exception that are cleared :: :: "+ie);
                    finalClearedTaxException = ie;
				   }
            }
        }
        Log.customer.debug(
                "CatSAPInvoiceReconciliationEngine ::: " +
                "Final Tax Exception: %s", finalTaxException);
        Log.customer.debug(
                "CatSAPInvoiceReconciliationEngine ::: " +
                "Final Resolved Tax Exception: %s", finalClearedTaxException);
        Log.customer.debug(
                "CatSAPInvoiceReconciliationEngine ::: " +
                "Exceptions to be removed: %s", exceptionsToBeRemoved);

        if (exceptionsToBeRemoved.size() > 0) {
            if (finalTaxException != null || finalClearedTaxException != null) {

                Log.customer.debug(
                        "CatSAPInvoiceReconciliationEngine ::: " +
                        "Removing exceptions: %s", exceptionsToBeRemoved);
                ListUtil.removeEqualElements(oldExceptions, exceptionsToBeRemoved);
                if (finalTaxException != null) {
                    Log.customer.debug(
                            "CatSAPInvoiceReconciliationEngine ::: " +
                            "Adding final tax exception: %s", finalTaxException);
					oldExceptions.add(finalTaxException);

                }
                else {
                    Log.customer.debug(
                            "CatSAPInvoiceReconciliationEngine ::: " +
                            "Adding final resolved tax exception: %s",
                            finalClearedTaxException);
                    oldExceptions.add(finalClearedTaxException);
                }
            }
        }
        Log.customer.debug(
                "CatSAPInvoiceReconciliationEngine ::: " +
                "Custom - Done with cleaning the exceptions");
        return mergeExceptions;
    }


	/*

	 *	Generate any custom Line Exceptions

	 */

	private List generateLineExceptions(InvoiceReconciliationLineItem irli, List typesToValidate, List exceptions) {

		return exceptions;

	}

    protected boolean validateBody(Approvable approvable) {

        Assert.that(approvable instanceof InvoiceReconciliation, "%s.validateBody: approvable must be an IR", "CatSAPInvoiceReconciliationEngine");

        Log.customer.debug("CatSAPInvoiceReconciliationEngine.validateBody called with %s", approvable);

        InvoiceReconciliation ir = (InvoiceReconciliation)approvable;

        Iterator lineItems = ir.getLineItemsIterator();

        boolean anyExceptions = super.validateBody(approvable);

        InvoiceReconciliationLineItem li;



        for(; lineItems.hasNext(); ) {

            li = (InvoiceReconciliationLineItem)lineItems.next();

            String supplierId = null;

            if (li.getSupplier() != null)

            {

				supplierId = li.getSupplier().getUniqueName();

			}

			else

			{

				return super.validateBody(approvable);

			}



			Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: freight line exception and supplierid= %s", supplierId);



			Iterator exceptions = li.getExceptionsIterator();

        	while(exceptions.hasNext()) {

            	InvoiceException invException = (InvoiceException)exceptions.next();



            	/*if(invException != null

            				&& invException.getType().getUniqueName().equals(FREIGHT_VARIANCE)

            				&& !StringUtil.nullOrEmptyOrBlankString(supplierId) ) {



						Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: freightSupplierArr=**"+ ListUtil.listToString(ListUtil.arrayToList(freightSupplierArr), "**"));



						if((StringUtil.occurs(freightSupplierArr, supplierId))>0){

							invException.setState(ReconciliationException.Accepted);

							Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Accepting the freight variance");

						} else {

							Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Disputing the freight variance");

							invException.setState(ReconciliationException.Disputed);

						}



					}//freight inv exception*/

            	if(invException != null

        				&& invException.getType().getUniqueName().equals(SPECIAL_VARIANCE) ) {

            		Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Disputing the special variance");

					invException.setState(ReconciliationException.Disputed);

            	}

			}// end exception iterator

        } //end line iterator

        return super.validateBody(approvable);

    }


	private void checkIfIRToBeRejected(Approvable approvable){

		//if (Log.customer.debugOn)
			Log.customer.debug("%s ::: Entering the method checkIfIRToBeRejected", ClassName);
			InvoiceReconciliation ir = (InvoiceReconciliation) approvable;

		if (!ir.getInvoice().isStandardInvoice()) {
			return;
		}

		Invoice invoice = ir.getInvoice();
		BaseVector irLineItems = ir.getLineItems();
		InvoiceReconciliationLineItem irLineItem = null;

		// Start :  Q4 2013 - RSD111 - FDD 3.0/TDD 1.1

		ProcureLineItem pli = null;
		Address irShipTo = null;
		Address plicShipTo = null;

		if((ir.getFieldValue("CompanyCode") != null) && (ir.getDottedFieldValue("CompanyCode.SAPSource").equals("MACH1")))
		{
			//Get 1st line item from IR
			Log.customer.debug("Entering the method checkIfIRToBeRejected...Its MACH1 IR");
			irLineItem = (InvoiceReconciliationLineItem)irLineItems.get(0);

			irShipTo = irLineItem.getShipTo();

			ProcureLineItemCollection plic = irLineItem.getOrder();
			if (plic == null)
			{
				plic = irLineItem.getMasterAgreement();
			}


			if (plic != null)
			{
				// Get ShipTo of 1st line item of order/contract line item
				pli = (ProcureLineItem) plic.getLineItems().get(0);
				plicShipTo = pli.getShipTo();

				if(plicShipTo != null)
				{

				String irCountry = (String)irShipTo.getDottedFieldValue("Country.UniqueName");
				String plicCountry = (String)plicShipTo.getDottedFieldValue("Country.UniqueName");

				Log.customer.debug("checkIfIRToBeRejected :: IR and POLI country field are: %s, %s",irCountry,plicCountry);

				String irCity = (String)irShipTo.getDottedFieldValue("PostalAddress.City");
				String irState = (String)irShipTo.getDottedFieldValue("PostalAddress.State");

				Log.customer.debug("checkIfIRToBeRejected :: IR city and state fields are: %s, %s",irCity,irState);

				String plicCity = (String)plicShipTo.getDottedFieldValue("PostalAddress.City");
				String plicState = (String)plicShipTo.getDottedFieldValue("PostalAddress.State");

				Log.customer.debug("checkIfIRToBeRejected :: POLI city and state fields are: %s, %s",plicCity,plicState);

				//String irCCCountry = (String)ir.getDottedFieldValue("CompanyCode.RegisteredAddress.Country.UniqueName")

				if(ir.getDottedFieldValue("CompanyCode.RegisteredAddress") != null)
				{

				if(ir.getDottedFieldValue("CompanyCode.RegisteredAddress.Country.UniqueName").equals("US"))
				{
					Log.customer.debug("checkIfIRToBeRejected :: IR belongs to US");

					//Its US IR compare the Shipto using country,city and state

					if((!plicCountry.equals(irCountry)) || (!plicCity.equalsIgnoreCase(irCity)) || (!plicState.equalsIgnoreCase(irState)) )
					{

						Log.customer.debug("checkIfIRToBeRejected ::: The IR will be auto rejected as the Shipto address does not match ShipTo on PO/Contract LI");

						//Rejecting the invoice since IR shipTo does not match Order/MA Shipto
						Log.customer.debug("checkIfIRToBeRejected ::: Rejecting UI Invoice as IR ShipTo does not match Order ShipTo");

						String rejectionMsg = "Invoice Reconciliation ShipTo is different from Order/MA ShipTo";

						LongString commentTxt = new LongString(rejectionMsg);

						String commentTitle = "Reason For Invoice Rejection";

						Date commentDate = new Date();

						User commentUser = User.getAribaSystemUser(ir.getPartition());

						CatTaxUtil.addCommentToIR(ir, commentTxt, commentTitle, commentDate, commentUser);

						ir.reject();

						return;
					}
				}
				else
				{
					Log.customer.debug("checkIfIRToBeRejected :: IR is Non US");
					//Its Non-US IR comparison only to be made between shipto using Country

					if(!plicCountry.equals(irCountry))
					{
						Log.customer.debug("checkIfIRToBeRejected  ::: on US IR -- The IR will be auto rejected as the Shipto address does not match ShipTo on PO/Contract LI");

						//Rejecting the invoice since IR shipTo does not match Order/MA Shipto
						Log.customer.debug("checkIfIRToBeRejected ::: Rejecting UI Invoice as IR ShipTo does not match Order ShipTo");

						String rejectionMsg = "Invoice Reconciliation ShipTo is different from Order/MA ShipTo";

						LongString commentTxt = new LongString(rejectionMsg);

						String commentTitle = "Reason For Invoice Rejection";

						Date commentDate = new Date();

						User commentUser = User.getAribaSystemUser(ir.getPartition());

						CatTaxUtil.addCommentToIR(ir, commentTxt, commentTitle, commentDate, commentUser);

						ir.reject();

						return;
					}
				}

		}

			}

				}

			}


		// End :  Q4 2013 - RSD111 - FDD 3.0/TDD 1.1


		for(int i=0;i<irLineItems.size();i++)
		{
			irLineItem = (InvoiceReconciliationLineItem)irLineItems.get(i);
			ProcureLineType lineType = (ProcureLineType)irLineItem.getLineType();

			if(lineType!=null && lineType.getCategory()==1){
			String autoRejectIRForUPV = (String)ir.getDottedFieldValue("CompanyCode.AutoRejectIRForUPV");
				if(autoRejectIRForUPV !=null && autoRejectIRForUPV.equalsIgnoreCase("Y") && !unitPriceWithinTolerence(irLineItem) && !isMANonCatItem(irLineItem))
				{
					//if (Log.customer.debugOn)

						Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Rejecting Invoice for Unit Price Variance");

					String rejectionMessage = "Caterpillar does not accept Unit Price Variance on invoice";

					LongString commentText = new LongString(rejectionMessage);

					String commentTitle = "Reason For Invoice Rejection";

					Date commentDate = new Date();

					User commentUser = User.getAribaSystemUser(ir.getPartition());

					CatTaxUtil.addCommentToIR(ir, commentText, commentTitle, commentDate, commentUser);

					ir.reject();

					return;

				}
			}
		}



		boolean isTaxLineLevel = false;


		if (invoice.getLoadedFrom() == Invoice.LoadedFromEForm || invoice.getLoadedFrom() == Invoice.LoadedFromUI) {

			//if invoice was created using the invoice eform or invoice entry screen (paper invoice)

			int numberOfTaxLines = 0;

			int numberOfTaxDetails = 0;
           Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB additionaleFormLinesPresent=>4 " + additionaleFormLinesPresent);

			//Start: Mach1 R5.5 (FRD2.8/TD2.8)
			for (int i = 0; i < irLineItems.size(); i++) {
				Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB additionaleFormLinesPresent=>1 " + additionaleFormLinesPresent);

				irLineItem = (InvoiceReconciliationLineItem) irLineItems.get(i);
				Log.customer.debug(" CatSAPInvoiceReconciliationEngine MDB Description eForm 444=> " +irLineItem.getDottedFieldValue("Description.Description"));

				if (irLineItem.getLineType() != null && irLineItem.getLineType().getCategory() == ProcureLineType.TaxChargeCategory &&
				!irLineItem.getDottedFieldValue("Description.Description").equals("Exempt - art. 15 DPR 633/72 n. 166/2012 DEL")) {
                    Log.customer.debug(" CatSAPInvoiceReconciliationEngine MDB inside tax line for eForm=> ");

					numberOfTaxLines = numberOfTaxLines + 1;
                    Log.customer.debug(" CatSAPInvoiceReconciliationEngine MDB just after lst loop eForm=> " +numberOfTaxLines);

				}
		        if (irLineItem.getLineType().getCategory() == ProcureLineType.LineItemCategory) {
							//Start: Mach1 R5.5 (FRD2.7/TD2.7)
							Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB 6 eForm=> " + additionaleFormLinesPresent);
		                 	if ((irLineItem.getFieldValue("OrderLineItem") == null) && (irLineItem.getFieldValue("MALineItem") == null)){
                              Money itemAmt = irLineItem.getAmount();
                              BigDecimal itemAmtValue = (BigDecimal)itemAmt.getAmount();
                              BigDecimal value = new BigDecimal(3);
		                     Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB 6 eForm itemAmt=> " + itemAmt);
		                     Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB 6 eForm value=> " + value);
		                     Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB 6 ASN itemAmtValuecnkmccmcmccmnxx=> " + itemAmtValue);
		                     Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB 6 eForm itemAmt.compareTo(value=> " + itemAmtValue.compareTo(value));
	                        if(itemAmtValue.compareTo(value) < 0) {
							    additionaleFormLinesPresent = additionaleFormLinesPresent + 1;
							    Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside amount additionaleFormLinesPresent=>2 " + additionaleFormLinesPresent);
                                Partition currentPartition = ir.getPartition();
								String qryString = "select this from ariba.basic.core.CommodityCode where CommodityCode.UniqueName = '931617'";
							    Log.customer.debug("final query : ValidSAPCommodityExportMapEntry: %s", qryString);
								AQLQuery query = AQLQuery.parseQuery(qryString);
								AQLOptions options = new AQLOptions(currentPartition);
								AQLResultCollection results = Base.getService().executeQuery(query,options);
								Log.customer.debug("final results : CatSAPInvoiceReconciliationEngine: %s", results);

								while(results.next()) {
								   	BaseId bid = results.getBaseId(0);
								    Log.customer.debug(" Preferred Ordering Method is " +bid);

                        ariba.procure.core.LineItemProductDescription desc = (ariba.procure.core.LineItemProductDescription)irLineItem.getDescription();
                        desc.setCommonCommodityCodeId(bid);
						Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB uNSPSCCode=> " +irLineItem.getDottedFieldValue("Description.CommonCommodityCode.UniqueName"));
							}
							//End: Mach1 R5.5 (FRD2.7/TD2.7)
							//Start: Mach1 R5.5 (FRD2.6/TD2.6)
							ClusterRoot acctcategory = (ClusterRoot)ir.getDottedFieldValue("LineItems[0].AccountCategory");
							Log.customer.debug(" CatSAPInvoiceReconciliationEngine acctcategory value is => " +acctcategory);
							irLineItem.setDottedFieldValue("AccountCategory",acctcategory);
							String accntcategorystr = (String)irLineItem.getDottedFieldValue("AccountCategory.UniqueName");
				            Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB: IR AccountCategory value is => " +accntcategorystr);
				            String Costcentertxt = (String)ir.getDottedFieldValue("LineItems[0].Accountings.SplitAccountings[0].CostCenterText");
						    Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB: IR firstlinecostcentertxt: Costcentertxt value is => " +Costcentertxt);
							String internalordertxt = (String)ir.getDottedFieldValue("LineItems[0].Accountings.SplitAccountings[0].InternalOrderText");
						    Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB: IR firstlineinternalordertxt: internalordertxt value is => " +internalordertxt);
							String wbselementtxt = (String)ir.getDottedFieldValue("LineItems[0].Accountings.SplitAccountings[0].WBSElementText");
						    Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB: IR firstlinewbselementtxt: WBSElementText value is => " +wbselementtxt);

			                SplitAccountingCollection  sac = (SplitAccountingCollection)irLineItem.getDottedFieldValue("Accountings");
			                Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB SplitAccountingCollection=> " +sac);
			                List splitAccountings = (List)sac.getDottedFieldValue("SplitAccountings");
			                Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB splitAccountings=> " +splitAccountings);

				      		if(splitAccountings!=null){
                      		for(int n=0;n<splitAccountings.size();n++){
							Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB GL acc=> " +n);
                 			SplitAccounting sa = (SplitAccounting)splitAccountings.get(n);
                 			sa.setDottedFieldValue("GeneralLedgerText","5602001000");
                 			Log.customer.debug(" GeneralLedgerText => " + sa.getDottedFieldValue("GeneralLedgerText"));
                 			sa.setDottedFieldValue("CostCenterText",Costcentertxt);
                 			Log.customer.debug(" CostCenterText => " + sa.getDottedFieldValue("CostCenterText"));
							sa.setDottedFieldValue("InternalOrderText",internalordertxt);
                 			Log.customer.debug(" InternalOrderText=> " + sa.getDottedFieldValue("InternalOrderText"));
							sa.setDottedFieldValue("WBSElementText",wbselementtxt);
                 			Log.customer.debug(" WBSElementText=> " + sa.getDottedFieldValue("WBSElementText"));
							//End: Mach1 R5.5 (FRD2.6/TD2.6)
							 }
						   } // end of sa
					      }
					    }
					  }
                   Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB additionaleFormLinesPresent=>3 " + additionaleFormLinesPresent);
		           //End: Mach1 R5.5 (FRD2.8/TD2.8)
              }



			numberOfTaxDetails = (ir.getTaxDetails()).size();
			Log.customer.debug("%s ::: IREngine numberOfTaxDetails", +numberOfTaxDetails);


			//Start: Mach1 R5.5 (FRD2.8/TD2.8)
			if ((numberOfTaxLines > 2) || (numberOfTaxDetails > 1)) {
			//End: Mach1 R5.5 (FRD2.8/TD2.8)
				//if (Log.customer.debugOn)

					Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Rejecting UI Invoice as more than one tax line");

				String rejectionMessage = "Caterpillar does not accept Multiple taxes on one invoice";

				LongString commentText = new LongString(rejectionMessage);

				String commentTitle = "Reason For Invoice Rejection";

				Date commentDate = new Date();

				User commentUser = User.getAribaSystemUser(ir.getPartition());

				CatTaxUtil.addCommentToIR(ir, commentText, commentTitle, commentDate, commentUser);

				ir.reject();

				return;

			}

			return;

		}



		if (invoice.getLoadedFrom() == Invoice.LoadedFromACSN || invoice.getLoadedFrom() == Invoice.LoadedFromFile) {

			int additionalAC = 0;

			int numberOfTaxLines = 0;

			int numberOfTaxDetails = 0;

			boolean isSUTPresent = false;
			//Start: Mach1 R5.5 (FRD2.5/TD2.5)
			//boolean additionalLinesPresent = false;
			int additionalLinesPresent = 0;
			//End: Mach1 R5.5 (FRD2.5/TD2.5)
			boolean additionalSpecialChargePresent = false;

			boolean headerSpecialCharge = false;

			//Start Q4 2013-RSD119-FDD5.0/TDD1.0
			try
			{
					boolean invAgainstOIOProcessReq = false;

		            PurchaseOrder po = ir.getOrder();
		            if (po != null)
		            {

		                    Object isOIOProcessReqB = po.getDottedFieldValue("OIOAgreement");
		                    boolean isOIOProcessReq = false;
		                    if (isOIOProcessReqB != null){
		                            isOIOProcessReq = ((Boolean)isOIOProcessReqB).booleanValue();
		                    }

		                    if (isOIOProcessReq){

		                                    Log.customer.debug("%s ::: Invoice against IBM OIO Requisition %s", ClassName, ir.getUniqueName());
		                            invAgainstOIOProcessReq = isOIOProcessReq;
		                    }
		            }

		            if (invAgainstOIOProcessReq){

		                Log.customer.debug("%s ::: Rejecting Electronic Invoice as Invoice against IBM OIO Requisition",ClassName);

		                String rejectionMessage = ResourceService.getString("cat.java.sap","INV_OIORejectionMessage");
		                LongString commentText = new LongString(rejectionMessage);
		                String commentTitle = ResourceService.getString("cat.java.sap","INV_OIORejectionTitle");
		                Date commentDate = new Date();
		                User commentUser = User.getAribaSystemUser(ir.getPartition());
		                CatTaxUtil.addCommentToIR(ir, commentText, commentTitle, commentDate, commentUser);

		                ir.reject();
		                Log.customer.debug("%s ::: Rejected Electronic Invoice as Invoice against IBM OIO Requisition",ClassName);
		                return;
		            }
			}
			catch (Exception e)
			{
				Log.customer.debug("Exception Occured : " + e);

				Log.customer.debug("Exception Details :" + ariba.util.core.SystemUtil.stackTrace(e));
			}

            //End Q4 2013-RSD119-FDD5.0/TDD1.0




			BaseVector taxDetails = invoice.getTaxDetails();

			if (taxDetails != null && !taxDetails.isEmpty()) {

				String taxType = ((TaxDetail) (taxDetails.get(0))).getCategory();

				if (taxType.equals("other")) {

					String rejectionMessage = "Caterpillar does not accept tax category \"other\" from ASN";

					LongString commentText = new LongString(rejectionMessage);

					String commentTitle = "Reason For Invoice Rejection";

					Date commentDate = new Date();

					User commentUser = User.getAribaSystemUser(ir.getPartition());

					CatTaxUtil.addCommentToIR(ir, commentText, commentTitle, commentDate, commentUser);

					ir.reject();

					return;

				}

			}

            Log.customer.debug(" CatSAPInvoiceReconciliationEngine Invoice load from ASN **3**=> " +additionalLinesPresent);

			for (int i = 0; i < irLineItems.size(); i++) {
				Log.customer.debug(" CatSAPInvoiceReconciliationEngine Invoice load from ASN **4**=> " +additionalLinesPresent);

				irLineItem = (InvoiceReconciliationLineItem) irLineItems.get(i);

               Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB irLineItem=> " +irLineItem);

				if(irLineItem.getLineType() != null){

					if ((irLineItem.getLineType().getCategory() == ProcureLineType.DiscountCategory)

						|| (irLineItem.getLineType().getCategory() == ProcureLineType.HandlingChargeCategory)) {


							String supplierId = null;

							if (irLineItem.getSupplier() != null)

							{
                               Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB 3=> " +additionalLinesPresent);
								supplierId = irLineItem.getSupplier().getUniqueName();

							}

							else

							{

								ir.reject();
                                   Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB 4=> " +additionalLinesPresent);
								return;

							}


							/*if ( (StringUtil.occurs(freightSupplierArr, supplierId))==0

									&& (irLineItem.getLineType().getCategory() == ProcureLineType.FreightChargeCategory) ) {

								Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Rejecting Electronic Invoice for freight from other supplier");

								additionalAC = additionalAC + 1;

							}*/

					}

					if ((irLineItem.getLineType().getCategory() == ProcureLineType.SpecialChargeCategory)) {

						Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB 5=> " +additionalLinesPresent);

						additionalSpecialChargePresent = true;

						if (irLineItem.getOrderLineItem() == null && irLineItem.getMALineItem() == null) {

							headerSpecialCharge = true;

						}

					}

					if (irLineItem.getLineType().getCategory() == ProcureLineType.TaxChargeCategory) {

						if ("ServiceUseTax".equals(irLineItem.getLineType().getUniqueName())

							|| "SalesTaxCharge".equals(irLineItem.getLineType().getUniqueName())) {

							isSUTPresent = true;

						}

						if (irLineItem.getDottedFieldValue("MatchedLineItem") != null) {

							isTaxLineLevel = true;
							Log.customer.debug(" CatSAPInvoiceReconciliationEngine isTaxLineLevel is true");

						}

						numberOfTaxLines = numberOfTaxLines + 1;
						Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB numberOfTaxLines14234=> " +numberOfTaxLines);

					}
                    //Start: Mach1 R5.5 (FRD2.5/TD2.5)
                    Log.customer.debug(" CatSAPInvoiceReconciliationEngine Invoice load from ASN **4**=> " +additionalLinesPresent);
					Log.customer.debug(" CatSAPInvoiceReconciliationEngine Invoice load from ASN **5**=> " +additionalLinesPresent);
                    if (irLineItem.getLineType().getCategory() == ProcureLineType.LineItemCategory) {
					Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB 6=> " +additionalLinesPresent);
                    if ((irLineItem.getFieldValue("OrderLineItem") == null) && (irLineItem.getFieldValue("MALineItem") == null)){
		                  Money itemAmt = irLineItem.getAmount();
		                  BigDecimal itemAmtValue = (BigDecimal)itemAmt.getAmount();
		                  BigDecimal value = new BigDecimal(3);
		                  Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB 6 ASN itemAmt=> " + itemAmt);
						  Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB 6 ASN value=> " + value);
						  Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB 6 ASN itemAmtValue758337853-875=> " + itemAmtValue);
						  Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB 6 ASN ASN itemAmt.compareTo(value=> " + itemAmtValue.compareTo(value));
		                  //Start: Mach1 R5.5 (FRD2.7/TD2.7)
						  if( itemAmtValue.compareTo(value) < 0) {
						  additionalLinesPresent = additionalLinesPresent + 1;
		                  Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB 6 ASN additionalLinesPresent=>8676 " + additionalLinesPresent);
		                  Partition currentPartition = ir.getPartition();
						  String qryString = "select this from ariba.basic.core.CommodityCode where CommodityCode.UniqueName = '931617'";
						  Log.customer.debug("final query : ValidSAPCommodityExportMapEntry: %s", qryString);
						  AQLQuery query = AQLQuery.parseQuery(qryString);
						  AQLOptions options = new AQLOptions(currentPartition);
						  AQLResultCollection results = Base.getService().executeQuery(query,options);
						  Log.customer.debug("final results : CatSAPInvoiceReconciliationEngine: %s", results);

						  while(results.next()) {
						  BaseId bid = results.getBaseId(0);
						  Log.customer.debug(" Preferred Ordering Method is " +bid);

                          ariba.procure.core.LineItemProductDescription desc = (ariba.procure.core.LineItemProductDescription)irLineItem.getDescription();
                          desc.setCommonCommodityCodeId(bid);
						  Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB uNSPSCCode=> " +irLineItem.getDottedFieldValue("Description.CommonCommodityCode.UniqueName"));
						}
						//End: Mach1 R5.5 (FRD2.7/TD2.7)
								//Start: Mach1 R5.5 (FRD2.6/TD2.6)
								ClusterRoot acctcategory = (ClusterRoot)ir.getDottedFieldValue("LineItems[0].AccountCategory");
								Log.customer.debug(" CatSAPInvoiceReconciliationEngine acctcategory value is => " +acctcategory);
								irLineItem.setDottedFieldValue("AccountCategory",acctcategory);
								String accntcategorystr = (String)irLineItem.getDottedFieldValue("AccountCategory.UniqueName");
				                Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB: IR AccountCategory value is => " +accntcategorystr);
				                String Costcentertxt = (String)ir.getDottedFieldValue("LineItems[0].Accountings.SplitAccountings[0].CostCenterText");
						        Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB: IR firstlinecostcentertxt: Costcentertxt value is => " +Costcentertxt);
								String internalordertxt = (String)ir.getDottedFieldValue("LineItems[0].Accountings.SplitAccountings[0].InternalOrderText");
						        Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB: IR firstlineinternalordertxt: internalordertxt value is => " +internalordertxt);
								String wbselementtxt = (String)ir.getDottedFieldValue("LineItems[0].Accountings.SplitAccountings[0].WBSElementText");
						        Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB: IR firstlinewbselementtxt: WBSElementText value is => " +wbselementtxt);
								SplitAccountingCollection  sac = (SplitAccountingCollection)irLineItem.getDottedFieldValue("Accountings");
				                Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB SplitAccountingCollection=> " +sac);
				                List splitAccountings = (List)sac.getDottedFieldValue("SplitAccountings");
				                Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB splitAccountings=> " +splitAccountings);
				      			if(splitAccountings!=null){
                            	for(int n=0;n<splitAccountings.size();n++){
								Log.customer.debug(" CatSAPInvoiceReconciliationEngine inside MDB GL acc=> " +n);
                 				SplitAccounting sa = (SplitAccounting)splitAccountings.get(n);
                 				sa.setDottedFieldValue("GeneralLedgerText","5602001000");
                 				Log.customer.debug(" GeneralLedgerText => " + sa.getDottedFieldValue("GeneralLedgerText"));
                 				sa.setDottedFieldValue("CostCenterText",Costcentertxt);
		                 		Log.customer.debug(" CostCenterText => " + sa.getDottedFieldValue("CostCenterText"));
								sa.setDottedFieldValue("InternalOrderText",internalordertxt);
		                 		Log.customer.debug(" InternalOrderText=> " + sa.getDottedFieldValue("InternalOrderText"));
								sa.setDottedFieldValue("WBSElementText",wbselementtxt);
		                 		Log.customer.debug(" WBSElementText=> " + sa.getDottedFieldValue("WBSElementText"));
								//End: Mach1 R5.5 (FRD2.6/TD2.6)
								 }
							   } // end of sa
						      }
						    }
						  }


                         Log.customer.debug(" CatSAPInvoiceReconciliationEngine outside MDB => " +additionalLinesPresent);
						 //End: Mach1 R5.5 (FRD2.5/TD2.5)
					}

			}



			if (headerSpecialCharge){

				//if (Log.customer.debugOn)

					Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Rejecting Electronic Invoice as Header Special Charge");

				String rejectionMessage = "Caterpillar does not accept Special Charge charged at header level";

				LongString commentText = new LongString(rejectionMessage);

				String commentTitle = "Reason For Invoice Rejection";

				Date commentDate = new Date();

				User commentUser = User.getAribaSystemUser(ir.getPartition());

				CatTaxUtil.addCommentToIR(ir, commentText, commentTitle, commentDate, commentUser);

				ir.reject();

				return;

			}


            //Start: Mach1 R5.5 (FRD2.5/TD2.5)
			if (additionalLinesPresent > 1) {
			//End: Mach1 R5.5 (FRD2.5/TD2.5)
				//if (Log.customer.debugOn)

					Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Rejecting Electronic Invoice as Additional Material Line");

				String rejectionMessage = "Caterpillar does not accept invoices with additional material lines than from the PO";

				LongString commentText = new LongString(rejectionMessage);

				String commentTitle = "Reason For Invoice Rejection";

				Date commentDate = new Date();

				User commentUser = User.getAribaSystemUser(ir.getPartition());

				CatTaxUtil.addCommentToIR(ir, commentText, commentTitle, commentDate, commentUser);

				ir.reject();

				return;

			}


			numberOfTaxDetails = (ir.getTaxDetails()).size();


			//Start: Mach1 R5.5 (FRD1.2/TD1.2)
			/*if (isTaxLineLevel) {

				//if (Log.customer.debugOn)

					Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Rejecting Electronic Invoice as line level tax");

				String rejectionMessage = "Caterpillar does not accept line level taxes";

				LongString commentText = new LongString(rejectionMessage);

				String commentTitle = "Reason For Invoice Rejection";

				Date commentDate = new Date();

				User commentUser = User.getAribaSystemUser(ir.getPartition());

				CatTaxUtil.addCommentToIR(ir, commentText, commentTitle, commentDate, commentUser);

				ir.reject();

				return;

			}*/
			//End: Mach1 R5.5 (FRD1.2/TD1.2)

			/*
			if (additionalAC > 0) {

				//if (Log.customer.debugOn)

					Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Rejecting Electronic Invoice as an Additional Charge identified");

				String rejectionMessage = "Caterpillar does not accept Freight, Discount or Handling charges on Invoice";

				LongString commentText = new LongString(rejectionMessage);

				String commentTitle = "Reason For Invoice Rejection";

				Date commentDate = new Date();

				User commentUser = User.getAribaSystemUser(ir.getPartition());

				CatTaxUtil.addCommentToIR(ir, commentText, commentTitle, commentDate, commentUser);

				ir.reject();

				return;

			}
			*/
			//Start :  Mach1 R5.5 (FRD1.2/TD1.2)
		/*	if ((numberOfTaxLines > 1) || (numberOfTaxDetails > 1)) {

				//if (Log.customer.debugOn)

					Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Rejecting Electronic Invoice as more than one tax line");

				String rejectionMessage = "Caterpillar does not accept multiple taxes charged on an Invoice";

				LongString commentText = new LongString(rejectionMessage);

				String commentTitle = "Reason For Invoice Rejection";

				Date commentDate = new Date();

				User commentUser = User.getAribaSystemUser(ir.getPartition());

				CatTaxUtil.addCommentToIR(ir, commentText, commentTitle, commentDate, commentUser);

				ir.reject();

				return;

			}*/
			//End :  Mach1 R5.5 (FRD1.2/TD1.2)

			if (!CatCSVInvoiceSubmitHook.vatReasonablnessCheck(irLineItems)){

				//if (Log.customer.debugOn)

					Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Rejecting Electronic Invoice as VAT charged > 50%");

				String rejectionMessage = "Caterpillar does not accept VAT Charge greater than 50%";

				LongString commentText = new LongString(rejectionMessage);

				String commentTitle = "Reason For Invoice Rejection";

				Date commentDate = new Date();

				User commentUser = User.getAribaSystemUser(ir.getPartition());

				CatTaxUtil.addCommentToIR(ir, commentText, commentTitle, commentDate, commentUser);

				ir.reject();

				return;

			}



			return;

		}

	}
//Vertex change to include exception handler for B0,B3,B4 tax code with 0 supplier Amount

	private void checkIfIRHasInvalidTaxAmount(Approvable lic)
	{
		if (lic instanceof ProcureLineItemCollection) {

			Log.customer.debug(" :: Inside ProcureLineItem Collection :: %s", lic);
			ProcureLineItemCollection plic = (ProcureLineItemCollection) lic;
			BaseVector lineItems = plic.getLineItems();
			int intCountOfLineItems = lineItems.size();
			Log.customer.debug("%s :: Total Number of line items ::", intCountOfLineItems);
			String headerLevelTaxAmount = "";

			if(lic.getFieldValue("TaxAmount")!=null)
				{
					headerLevelTaxAmount= ((BigDecimal)lic.getDottedFieldValue("TaxAmount.Amount")).toString();
					Log.customer.debug("%s :: :: Header Level Tax Amount  :: :: "+headerLevelTaxAmount);

				}
				ProcureLineItem pli = null;

				for (int j = 0; j < intCountOfLineItems; j++) {
					// Procure Line Item object at line level
					pli = (ProcureLineItem) lineItems.get(j);


					String SAPTaxCode = (String) pli.getDottedFieldValue("TaxCode.SAPTaxCode");
					SAPTaxCode = (SAPTaxCode == null)? "" : SAPTaxCode;
					Log.customer.debug(" ::  TaxCode.SAPTaxCode :: "+ SAPTaxCode);

					if(SAPTaxCode.contains("B0")||SAPTaxCode.contains("B4")||SAPTaxCode.contains("B3"))
						{
							if(headerLevelTaxAmount != "0")
							{
							Log.customer.debug(" ::  IR has invalid suppler tax amount for B0,B4 :: "+ SAPTaxCode);
							Log.customer.debug(" ::  Please Add the tax manager ::");
							TAX_CALC_FAIL_FLAG_FOR_INVALID_TXAMT = "true";
		                 //   InvoiceExceptionType excType = InvoiceExceptionType.lookupByUniqueName("VertexTaxVariance", lic.getPartition());
						 //The exception added must also be added for IRApprovaLRules Util in the string array - taxApprovalRequired
    						}

					  }
						else
					  {
							if(!SAPTaxCode.equals("")){
								Log.customer.debug(" ::  the sap tax code is valid for vertex, do not add tax manager: "+ SAPTaxCode);
								TAX_CALC_FAIL_FLAG_FOR_INVALID_TXAMT = "false";
								break;
							}
					  }
					}//end of For loop

			}//End of if(lic instance of loop)
		}
		//End of vertex change for tax code exception handler
	private boolean unitPriceWithinTolerence(InvoiceReconciliationLineItem irli) {

		//Check if unit price is within defined tolerence.

		//Eg. unit price to be 1% of order line unit price and line amount total not to exceed 10 dollars

	    //unitPriceMaxTolerencePct = BigDecimalFormatter.round(unitPriceMaxTolerencePct, 2);

        //unitPriceMinTolerencePct = BigDecimalFormatter.getBigDecimalValue((100.00 - unitPriceTolerencePct)/100.00));

		InvoiceReconciliation ir = (InvoiceReconciliation)irli.getLineItemCollection();
		BigDecimal compUnitPriceTolerancePct = null;
		Money compLineAmountTolerance = null;
		if(ir!=null){
			compUnitPriceTolerancePct = (BigDecimal)ir.getDottedFieldValue("CompanyCode.UnitPriceTolerancePct");
			Log.customer.debug("%s :::orderOrMALineUnitPrice="+compUnitPriceTolerancePct, ClassName);

			compLineAmountTolerance = (Money)ir.getDottedFieldValue("CompanyCode.LineAmountTolerance");
			Log.customer.debug("%s :::orderOrMALineUnitPrice="+compLineAmountTolerance, ClassName);
		}

		try {
			// take the value from comapanycode incase companycode has it.
			if(compUnitPriceTolerancePct!=null){
				unitPriceTolerencePctDouble = compUnitPriceTolerancePct.doubleValue();
			}else{
				unitPriceTolerencePctDouble = DoubleFormatter.parseDouble(UnitPriceTolerance);
			}

		}catch(Exception dpe){unitPriceTolerencePctDouble = 0.0;}



		Money irLineUnitPrice = (Money)irli.getDescription().getPrice();  //ir line price

		Money orderOrMALineUnitPrice = null;



		BigDecimal irliQty = irli.getQuantity(); //ir line qty



		Log.customer.debug("%s :::irLineUnitPrice="+irLineUnitPrice.toString() +" irliQty="+irliQty, ClassName);



		//get order or ma line unit price to compare with irli unit price

		if (irli.getOrderLineItem() != null) {

			orderOrMALineUnitPrice = (Money)irli.getOrderLineItem().getDescription().getPrice();

		} else if (irli.getMALineItem() != null) {

			orderOrMALineUnitPrice = (Money)irli.getMALineItem().getDescription().getPrice();

		}



		if (orderOrMALineUnitPrice == null) return true;

		Log.customer.debug("%s :::orderOrMALineUnitPrice="+orderOrMALineUnitPrice.toString(), ClassName);



		if(irLineUnitPrice.compareTo(orderOrMALineUnitPrice) <= 0) return true;



		double diffPercent = Money.percentageDifference(irLineUnitPrice, orderOrMALineUnitPrice);

		Log.customer.debug("%s :::diffPercent="+diffPercent, ClassName);

		Log.customer.debug("%s :::unitPriceTolerencePctDouble="+unitPriceTolerencePctDouble, ClassName);

		if((unitPriceTolerencePctDouble - diffPercent) < 0 ) return false;



        //Check if the unit price is within tolerence, then the line amount total

        //must also be within tolerence

		// take delta of the two prices and multiply with irli qty and check if within tolerence

		Money delta = Money.subtract(orderOrMALineUnitPrice, irLineUnitPrice);

		if(delta.getSign() == -1) delta = delta.negate(); //change sign to positive



		delta = delta.multiply(irliQty); //multiply delta with irli qty


		Money amtTolerenceMoney = new Money(delta, lineAmountTolerenceInDollars);
		// take the value from comapanycode incase companycode has it.
		if(compLineAmountTolerance!=null){
			amtTolerenceMoney = compLineAmountTolerance;
		}

		Log.customer.debug("%s :::delta="+delta.toString() +" amtTolerenceMoney="+amtTolerenceMoney.toString(), ClassName);



		if (delta.compareTo(amtTolerenceMoney) > 0 ) {

			Log.customer.debug("%s :::returning false as the line amt is outside tolerance", ClassName);

			return false;

		}

		return true;

	}

	private boolean isMANonCatItem(InvoiceReconciliationLineItem irli){

		ContractLineItem mali = (ContractLineItem)irli.getMALineItem();
			if(mali != null){
				Boolean isAdhoc = (Boolean)mali.getFieldValue("IsAdHoc");
				if(isAdhoc!=null && isAdhoc.booleanValue()){
					return true;
				}
			}
		return false;
	}

}
