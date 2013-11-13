/*******************************************************************************************************************************************

        CatCSVInvoiceReconciliationEngine.java

        ChangeLog:
        Date            Name            History
        --------------------------------------------------------------------------------------------------------------
        07/13/2007      Chandra    Added freight exception for dell and ibm invoices. The freight creates a freight
                                                        exception and accepts the exception.

		08/13/2009		Vikram	   CR189	Auto-Reject
		19/10/2009      Vikram 	   CR189    Allow credit invoices
		12/11/2009      Vikram     CR189    Added extra check for contract close variance
		190  IBM AMS_Lekshmi  Auto Rejecting InvoiceReconciliation if the Order/Contract Currency different from Invoice Currency	
		287  IBM AMS_Rahul- 15/01/2013  Auto Rejecting InvoiceReconciliation if the Invoice Date is Future Date.
		02/21/2013  IBM Niraj Mach1 R5.5 (FRD1.5/TD1.3) Commented to allow multiple tax line for MACH1 R5.5 Release

*******************************************************************************************************************************************/

package config.java.invoicing.vcsv1;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import ariba.approvable.core.Approvable;
import ariba.base.core.Base;
import ariba.base.core.BaseObject;
import ariba.base.core.BaseVector;
import ariba.base.core.ClusterRoot;
import ariba.base.core.LongString;
import ariba.base.core.aql.AQLOptions;
import ariba.base.core.aql.AQLQuery;
import ariba.base.core.aql.AQLResultCollection;
import ariba.basic.core.Currency;
import ariba.basic.core.Money;
import ariba.common.core.SplitAccounting;
import ariba.common.core.SplitAccountingCollection;
import ariba.contract.core.Contract;
import ariba.invoicing.core.Invoice;
import ariba.invoicing.core.InvoiceException;
import ariba.statement.core.StatementReconciliation;
import ariba.invoicing.core.InvoiceLineItem;
import ariba.invoicing.core.InvoiceReconciliation;
import ariba.invoicing.core.InvoiceReconciliationLineItem;
import ariba.invoicing.core.Log;
import ariba.procure.core.ProcureLineItem;
import ariba.procure.core.ProcureLineType;
import ariba.purchasing.core.PurchaseOrder;
import ariba.reconciliation.core.ReconciliationException;
import ariba.tax.core.TaxDetail;
import ariba.user.core.User;
import ariba.util.core.Assert;
import ariba.util.core.Constants;
import ariba.util.core.Date;
import ariba.util.core.Fmt;
import ariba.util.core.ListUtil;
import ariba.util.core.ResourceService;
import ariba.util.core.StringUtil;
import ariba.util.formatter.BigDecimalFormatter;
import ariba.util.formatter.DoubleFormatter;
import ariba.util.formatter.IntegerFormatter;
import ariba.statement.core.StatementReconciliationLineItem;
import config.java.invoicing.CatInvoiceReconciliationEngine;
import config.java.tax.CatTaxUtil;


public class CatCSVInvoiceReconciliationEngine extends CatInvoiceReconciliationEngine {

        public static final String ClassName = "CatCSVInvoiceReconciliationEngine";
	 private static final String StringTable = "cat.java.common";
        private static final String UNMATCHED_INVOICE = "UnmatchedInvoice";
        private static final String MA_NOT_INVOICABLE = "MANotInvoiceable";
        private static final String FREIGHT_VARIANCE = "FreightVariance";
        private static final String OVER_TAX = "OverTaxVariance";
        private static final String TAX_CALC_FAIL = "CATTaxCalculationFailed";
        private static final String PO_RCVD_Q_V = "POReceivedQuantityVariance";
        private static final String MA_RCVD_Q_V = "MAReceivedQuantityVariance";
        private static final String CLOSE_ORDER = "ClosePOVariance";
		// CR189 -- (Vikram J Singh) -- Below 6 Variances
		private static final String CANCELLED_ORDER = "CancelledPOVariance";
		private static final String CLOSED_CONTRACT = "MACloseVariance";
		private static final String TRANS_COMP_VAR = "TransactionCompletedVariance";
		private static final String ZERO_AMOUNT="ZeroAmountVariance";
		private static final String MA_NOT_INVOICING = "MANotInvoicing";
		private static final String MA_LINE_DATE_VAR = "MALineDateVariance";

        private static final String PO_PRC_V = "POPriceVariance";
        private static final String PO_CATPRC_V = "POCatalogPriceVariance";
        private static final String PO_Line_AMT_V = "POLineAmountVariance";
        private static final String MA_NC_PRC_V = "MANonCatalogPriceVariance";
        private static final String MA_CAT_PRC_V = "MACatalogPriceVariance";
        private static final String MA_Line_AMT_V = "MALineAmountVariance";
    	public static final String ComponentStringTable = "cat.java.vcsv1.csv";

        private static final String UnitPriceTolerance = ResourceService.getString("cat.java.vcsv1","LineUnitPriceTolerenceAllowedInPercent");
    	private static double unitPriceTolerencePctDouble = 0.0;
    	//private static final BigDecimal unitPriceMaxTolerencePct = BigDecimalFormatter.getBigDecimalValue(((100.00 + unitPriceTolerencePct)/100.00));
    	//private static final BigDecimal unitPriceMinTolerencePct = BigDecimalFormatter.getBigDecimalValue((100.00 - unitPriceTolerencePct)/100.00));

        private static final String LineAmountTolerance = ResourceService.getString("cat.java.vcsv1","LineAmountTolerenceAllowedInDollars");
   		private static final BigDecimal lineAmountTolerenceInDollars = new BigDecimal(LineAmountTolerance);

        private static final String[] freightSupplierArr = StringUtil.delimitedStringToArray(ResourceService.getString("cat.java.vcsv1","SuppliersWhoCanChargeFreight"), ':');

        private String[] autoRejectExcTypes = { UNMATCHED_INVOICE, MA_NOT_INVOICABLE, FREIGHT_VARIANCE };
		private String[] ignoreExceptions = { OVER_TAX, PO_RCVD_Q_V, MA_RCVD_Q_V };
//		private static final String StringTable = "cat.java.common";

        public boolean reconcile(Approvable approvable) {
                if (approvable instanceof InvoiceReconciliation) {
                        //if (Log.customer.debugOn)
                                Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Executing Accounting and Field Assignment Method");

                        if (approvable.isCreationInProgress()){
                                //if (Log.customer.debugOn){
                                        Log.customer.debug("%s ::: Is Creation of the IR in progress " + approvable.isCreationInProgress(), ClassName);
                                        Log.customer.debug("%s ::: Running through to default the accounting as this is IR creation", ClassName);
                                //}
                                defaultAccountingOnIRLineItems(approvable);
                        }
                        else{
                                //if (Log.customer.debugOn)
                                        Log.customer.debug("%s ::: Is Creation of the IR in progress " + approvable.isCreationInProgress(), ClassName);
                        }

                        copyFieldsFromInvoice(approvable);
                        calculateDiscountedAmounts(approvable);
                        //if (Log.customer.debugOn)
                                Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Finished Accounting and Field Assignment Method");
                }
                return super.reconcile(approvable);
        }

        protected boolean validateHeader(Approvable approvable) {
                Assert.that(
                        approvable instanceof InvoiceReconciliation,
                        "%s.validateHeader: approvable must be an IR",
                        "config.java.invoicing.vcsv1.CatCSVInvoiceReconciliationEngine");

                InvoiceReconciliation ir = (InvoiceReconciliation) approvable;
                if (ir.getConsolidated()) {
                        //if summary invoice then auto-reject the IR
                        //if (Log.customer.debugOn)
                                Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Rejecting summary invoices for U.S. Corporate Division");
                        String rejectionMessage = "Caterpillar does not accept Summary Invoices";
                        LongString commentText = new LongString(rejectionMessage);
                        String commentTitle = "Reason For Invoice Rejection";
                        Date commentDate = new Date();
                        User commentUser = User.getAribaSystemUser(ir.getPartition());
                        CatTaxUtil.addCommentToIR(ir, commentText, commentTitle, commentDate, commentUser);
                        ir.reject();
                }

                // Reject invoices from ASN or EDI with any additional charges other than tax
                // Reject invoices with more than one header level tax
                // Reject invoices with any line level taxes charged
                checkIfIRToBeRejected(ir);
                
            	// Start Of Issue 190
        		autoRejectInvoiceCurrencyDiffFromOrderOrContract(ir);
        		// issue 190 End
        		
        		//Start of Issue 287
        		if (autoRejectInvoiceForFutureDate(ir))
        		{
        		  	Log.customer.debug("autoRejectInvoiceForFutureDate():Adding Comments to show the reason");	
				  String rejectionMessage = (String)ResourceService.getString(StringTable, "InvoiceFutureDate");
				  LongString commentText = new LongString(rejectionMessage);
				  String commentTitle = "Reason For Invoice Rejection";
				  Date commentDate = new Date().getNow();
				  User commentUser = User.getAribaSystemUser(ir.getPartition());
				  CatTaxUtil.addCommentToIR(ir, commentText, commentTitle, commentDate, commentUser);
				  Log.customer.debug("autoRejectInvoiceForFutureDate():IR is Auto Rejected");
				  ir.reject();
        		}
        		//End of Issue 287
        		
                return super.validateHeader(approvable);
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
                                Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: In method for generating exceptions");
                for (int i = 0; i < exceptions.size(); i++) {
                                Log.customer.debug(
                                                "CatCSVInvoiceReconciliationEngine ::: Exception generated is %s",
                                                ((InvoiceException) exceptions.get(i)).getType().getUniqueName());
                }
                return exceptions;
        }

        /*
         *  Returns Header Exceptions
         *	ARajendren, Ariba Inc.,
         *	Changed the method signature from InvoiceReconciliation to StatementReconciliation
         */

        protected List getExceptionTypesForHeader(StatementReconciliation ir) {
                //if (Log.customer.debugOn)
                        Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: In method for generating exception type for header");
                List exceptions = super.getExceptionTypesForHeader(ir);

                String invPurpose = "";
                String supplierEmailID = "MSC_Help@cat.com";
                String invoiceNumber = "";
                String singNumValue = "";
                Money  totalCost;
				Money  invtotalcost;
				BigDecimal tcAmount = null;
				Integer CatNum = null;
                BigDecimal tcAmount1= null;
				Money totalCostlessInv;
                BigDecimal pototalCostAmount = null;
                BigDecimal MATotalCostAmt = null;
                BigDecimal poAmountAccpeted = null;
                BigDecimal poAmountInvoiced = null;
                BigDecimal poAmountReconcilied = null;
				BigDecimal invAmountInvoiced = null;
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
						BigDecimal Amtleft = null;
						BigDecimal AmtCompareTo = null;
BigDecimal totalDisputed = null;
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
								AQLQuery query4;
								AQLOptions queryOptions;
								AQLOptions queryOptions4;
								AQLResultCollection queryResults;
								AQLResultCollection queryResults2;
								AQLResultCollection queryResults3;
								AQLResultCollection queryResults4;


                if ((ir.getFieldValue("taxCallNotFailed") == null)
                        || ((ir.getFieldValue("taxCallNotFailed") != null) && ((Boolean) ir.getFieldValue("taxCallNotFailed")).booleanValue())) {
                        //if (Log.customer.debugOn)
                                Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Removing exception type: %s", TAX_CALC_FAIL);
                        exceptions.remove(super.getInvoiceExceptionType(TAX_CALC_FAIL, ir.getPartition()));
                        //if (Log.customer.debugOn)
                                Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Successfully removed exception type: %s", TAX_CALC_FAIL);
                }

                // ********************* CR189 PO Code (Vikram J Singh) Starts *********************

				//Added by Sandeep to Check TotalCost Less Invoiced on the Invoice
				//ariba.purchasing.core.PurchaseOrder irOrder = (ariba.purchasing.core.PurchaseOrder)ir.getOrder();
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
							ariba.invoicing.core.Invoice irInvc = (ariba.invoicing.core.Invoice)(((InvoiceReconciliation) ir).getInvoice());
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
                                             tcAmount1= (BigDecimal) irLineItem.getDottedFieldValue("Amount.ApproxAmountInBaseCurrency");
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
Log.customer.debug("Getting the Amt in Base Currency for the DO" +pototalCostAmount);
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
    		   BigDecimal Tr = (BigDecimal) (Amtleft1.multiply(Tolrance));
  		                    Log.customer.debug("Amt of Tolerance to be added" +Tr);
  		                    Amtleft = Amtleft1.add(Tr);
  		         Log.customer.debug("Final Amt after adding Tolerance" +Amtleft);

  		       // Close order variance code
                      // if Closed = 1 then its open, if Closed = 5 then its closed               				
                      // Changed by Sandeep as 9r updates this field for Close and Open orders
                                                     
                                                 Integer closeOrder = (Integer) irOrder.getFieldValue("Closed");
                                                int closeState = closeOrder.intValue();
//		                   		Boolean closeOrder = (Boolean)irOrder.getFieldValue("CloseOrder");
		                       	//if (Log.customer.debugOn)
		                       		Log.customer.debug("%s::: CloseOrder Value IS: %s...",ClassName,closeOrder);
                         //             	if (closeOrder.booleanValue() && !singNumValue.equals("-1"))
                                             if ((closeState == 4 || closeState == 5) && !singNumValue.equals("-1"))
		                       	{
		                       		//if (Log.customer.debugOn)
		                           		Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: CloseOrder Value IS: %s... Hence NOT removing the %s exception", (Boolean)ir.getDottedFieldValue("Order.CloseOrder"), CLOSE_ORDER);
		                       	}
		                       	else
		                       	{
		                           	//if (Log.customer.debugOn)
		                               	Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Removing exception type: %s", CLOSE_ORDER);
		                           	exceptions.remove(super.getInvoiceExceptionType(CLOSE_ORDER, ir.getPartition()));
		                           	//if (Log.customer.debugOn)
		                               	Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Successfully removed exception type: %s", CLOSE_ORDER);
		                       	}

		       					// Cancel order variance code

		       					Integer orderedState = (Integer)ir.getDottedFieldValue("Order.OrderedState");
					int orderStatetemp = orderedState.intValue();


					//if (Log.customer.debugOn)
                		Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: CancelledOrder Code");
                	//if (( ir.getDottedFieldValue("Order.OrderedState") != null ) && (orderStatetemp == 16))
					if (( ir.getDottedFieldValue("Order.OrderedState") != null ) && (orderStatetemp == 16) && !singNumValue.equals("-1"))
					{
                		//if (Log.customer.debugOn)
                    		Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: CancelledOrder Value IS: %s... Hence NOT removing the %s exception", ir.getDottedFieldValue("Order.OrderedState"), CANCELLED_ORDER);
                	}

					else
                	{
                    	//if (Log.customer.debugOn)
                    		Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Removing exception type: %s", CANCELLED_ORDER);
                    	exceptions.remove(super.getInvoiceExceptionType(CANCELLED_ORDER, ir.getPartition()));
                    	//if (Log.customer.debugOn)
                            Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Successfully removed exception type: %s", CANCELLED_ORDER);
                	}

                	// Transaction Completed Variance code

					//1. PO.TotalAmount = PO.ReceivedAmount = PO.PaidAmount; should auto reject
					//2. PO.TotalAmount < PO.ReceivedAmount = PO.PaidAmount; should auto reject
					//3. PO.TotalAmount = PO.ReceivedAmount < PO.PaidAmount; should auto reject
					//4. PO.TotalAmount < PO.ReceivedAmount < PO.PaidAmount; should auto reject
					//5. PO.TotalAmount = PO.AmountPaid < PO.ReceivedAmount, should auto reject
					//6. PO.TotalAmount > PO.ReceivedAmount = PO.PaidAmount; should NOT auto reject


					//if (Log.customer.debugOn)
                		Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Entering Transaction Completed Variance code" );
                	/*if (  ir.getDottedFieldValue("Order.TotalCost.Amount").equals(ir.getDottedFieldValue("Order.AmountAccepted.Amount")) && ir.getDottedFieldValue("Order.AmountAccepted.Amount").equals(ir.getDottedFieldValue("Order.AmountInvoiced.Amount"))&& !singNumValue.equals("-1"))*/
                	//if(strTCAmount.equals(strPOAmountAccepted) && strPOAmountAccepted.equals(strpoAmountInvoiced) && strpoAmountInvoiced.equals(strpoAmountReconcilied) && !singNumValue.equals("-1") )
                	//if( (poAmountAccpeted!=null) && (poAmountReconcilied!=null) && pototalCostAmount.compareTo(poAmountAccpeted) <=0 && poAmountAccpeted.compareTo(poAmountReconcilied)<=0 && !singNumValue.equals("-1"))
					//if( (poAmountAccpeted!=null) && (poAmountReconcilied!=null) && pototalCostAmount.compareTo(poAmountAccpeted) <=0 && ( (poAmountAccpeted.compareTo(poAmountReconcilied)<=0) || (poAmountAccpeted.compareTo(poAmountReconcilied)>0) ) && !singNumValue.equals("-1"))
                	//if( (poAmountAccpeted!=null) && (poAmountReconcilied!=null) && pototalCostAmount.compareTo(poAmountAccpeted) <=0 && ((poAmountAccpeted.compareTo(poAmountReconcilied)<=0) || ((poAmountAccpeted.compareTo(poAmountReconcilied)>0) && (pototalCostAmount.compareTo(poAmountReconcilied)==0))) && !singNumValue.equals("-1"))
					//
                	//	//if (Log.customer.debugOn)
                    //		Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Total Amount <=Amount Received <= Amount Reconciled... Hence NOT removing the %s exception", TRANS_COMP_VAR);

					//if (tcAmount.compareTo(pototalCostAmount) < 0 )
					//
						Log.customer.debug("Comparing the IR and the Amt left total FINAL ****");
						Log.customer.debug("IR Amt to be compared" +tcAmount);
					    Log.customer.debug("Amt left that is invoicable" +Amtleft);
						if (tcAmount.compareTo(Amtleft) >= 0 && !singNumValue.equals("-1"))
						{
							//if (Log.customer.debugOn)
			       			Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Transaction Completed... Hence NOT removing the %s exception", TRANS_COMP_VAR);




                    	/*ariba.common.core.Supplier irPOSupplier = (ariba.common.core.Supplier)ir.getSupplier();
                    	Log.customer.debug("%s:: (Transaction PO variance)Supplier ID::%s",ClassName,irPOSupplier.getUniqueName());
						*/

                    	/* BaseVector supplierLocation = (BaseVector)irPOSupplier.getLocations();

						for (int k = 0;k<supplierLocation.size();k++){

						SupplierLocation suppLocations = supplierLocation.get(k);

						String locationsEmailID = suppLocations.getFieldValue("EmailAddress");
						//TBD: what is location does not have email id.
						if(!StringUtil.nullOrEmptyOrBlankString(locationsEmailID)) continue;
					 	  Log.customer.debug("%s::Supplier location Email ID:%s",ClassName,locationsEmailID);
					  	supplierEmailID = locationsEmailID;

						}*/

						/*ariba.common.core.SupplierLocation irSupplierLocation = (ariba.common.core.SupplierLocation)ir.getSupplierLocation();

						if (irSupplierLocation!=null){

						String locationsEmailID = (String)irSupplierLocation.getFieldValue("EmailAddress");
						if(!StringUtil.nullOrEmptyOrBlankString(locationsEmailID))
							Log.customer.debug("%s::Supplier location Email ID:%s",ClassName,locationsEmailID);
						supplierEmailID = locationsEmailID;
						}*/

						//CatEmailNotificationUtil.sendNotification("Invoice"+invoiceNumber+ " Rejected","Invoice:"+invoiceNumber+ "rejected as the order is closed for invoicing in MSC",supplierEmailID,null);
						}

                 	else
                 	{
						 Log.customer.debug("DO is still INVOICABLE *** REMOVE TRANSCATION VARAINCE ***");
                         //if (Log.customer.debugOn)
                                 Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Removing exception type: %s", TRANS_COMP_VAR);
                         exceptions.remove(super.getInvoiceExceptionType(TRANS_COMP_VAR, ir.getPartition()));
                         //if (Log.customer.debugOn)
                                 Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Successfully removed exception type: %s", TRANS_COMP_VAR);
                 	}

				 	// Zero Amount Variance Code


				 	ariba.invoicing.core.Invoice irInvo = (ariba.invoicing.core.Invoice)(((InvoiceReconciliation) ir).getInvoice());
				 	if (irInvo!=null){
						invtotalcost = (Money)irInvo.getFieldValue("TotalCost");

						if (invtotalcost!=null) {

							invAmountInvoiced = (BigDecimal)invtotalcost.getAmount();
							strinvAmountInvoiced = BigDecimalFormatter.getStringValue(invAmountInvoiced);
							Log.customer.debug("%s::Amount Invoiced :%s",ClassName,strinvAmountInvoiced);
						}



						//if (Log.customer.debugOn)
                			Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Zero Amount Variance Code");
						if ( (invtotalcost!=null) && (strinvAmountInvoiced.equals("0")) )
						{
                			//if (Log.customer.debugOn)
                    			Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Amount Invoiced Value IS: %s... Hence NOT removing the %s exception", strinvAmountInvoiced, ZERO_AMOUNT);
						}

						else
						{
							//if (Log.customer.debugOn)
                    			Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Removing exception type: %s", ZERO_AMOUNT);
							exceptions.remove(super.getInvoiceExceptionType(ZERO_AMOUNT, ir.getPartition()));
							//if (Log.customer.debugOn)
								Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Successfully removed exception type: %s", ZERO_AMOUNT);
						}
				 	}

				// ********************* CR189 PO Code (Vikram J Singh) Ends *********************

                }

				// ********************* CR189 MA Code (Vikram J Singh) Starts *********************
                else {
					if(ir.getMasterAgreement() !=null)
			  {
                	ariba.contract.core.Contract irContract = (ariba.contract.core.Contract)ir.getMasterAgreement();

					if (irContract != null)
					{
						String MAbaseId = bo2.getBaseId().toDBString();
						Log.customer.debug("Base Id of MA is" + MAbaseId);
						ariba.invoicing.core.Invoice irInv = (ariba.invoicing.core.Invoice)(((InvoiceReconciliation) ir).getInvoice());
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
							                                Log.customer.debug("LineItem Type is not null on the IR");
															                               CatNum = (Integer)irLineItem.getDottedFieldValue("LineType.Category");
															                                Log.customer.debug("The Category of the Line items is " +CatNum.intValue());
                                                                        if ((CatNum.intValue()== 1))

							                                                {

							                                             Log.customer.debug("LineItem Type is catalog");
							                                                if(irLineItem.getAmount() !=null)
							                                                {
							                                             Log.customer.debug ("Getting Line Item amt for Type only catalog");
							                                             tcAmount1= (BigDecimal) irLineItem.getDottedFieldValue("Amount.ApproxAmountInBaseCurrency");
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

						//if (Log.customer.debugOn)
                            Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: CloseContract Code");
                    	if (( ir.getDottedFieldValue("MasterAgreement.MAState") != null ) && (contractState == 16) && !singNumValue.equals("-1") && ! irContract.getIsReceivable())
						{
                            //if (Log.customer.debugOn)
                            	Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: MAState Value IS: %s... Hence NOT removing the %s exception", ir.getDottedFieldValue("MasterAgreement.MAState"), CLOSED_CONTRACT);

                            /*ariba.common.core.Supplier irMASupplier = (ariba.common.core.Supplier)ir.getSupplier();
                            Log.customer.debug("%s:: (Close MA variance)Supplier ID::%s",ClassName,irMASupplier.getUniqueName());

                            BaseVector supplierLocation = (BaseVector)irMASupplier.getLocations();

                            for (int k = 0;k<supplierLocation.size();k++){

								 SupplierLocation suppLocations = supplierLocation.get(k);

								 String locationsEmailID = suppLocations.getFieldValue("EmailAddress");
								 //TBD: what is location does not have email id.
								 if(!StringUtil.nullOrEmptyOrBlankString(locationsEmailID)) continue;
								    Log.customer.debug("%s::Supplier location Email ID:%s",ClassName,locationsEmailID);
								    supplierEmailID = locationsEmailID;

							}
							ariba.common.core.SupplierLocation irSupplierLocation = (ariba.common.core.SupplierLocation)ir.getSupplierLocation();
							if (irSupplierLocation!=null){

								String locationsEmailID = (String)irSupplierLocation.getFieldValue("EmailAddress");
								if(!StringUtil.nullOrEmptyOrBlankString(locationsEmailID))
									Log.customer.debug("%s::Supplier location Email ID:%s",ClassName,locationsEmailID);
								supplierEmailID = locationsEmailID;
						     }*/
							//CatEmailNotificationUtil.sendNotification("Invoice"+invoiceNumber+ "  Rejected","Invoice:"+invoiceNumber+ " rejected as the Contract is Closed in MSC",supplierEmailID,null);



                    	}
            else
                        {
                                                        //if (Log.customer.debugOn)
                                                                Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Removing exception type: %s", CLOSED_CONTRACT);
                                                        exceptions.remove(super.getInvoiceExceptionType(CLOSED_CONTRACT, ir.getPartition()));
                                                        //if (Log.customer.debugOn)
                                                           Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Successfully removed exception type: %s", CLOSED_CONTRACT);
                                                }


			//else if ( irContract.getIsReceivable() && irContract.getIsInvoiceable() && !singNumValue.equals("-1"))
                     if (irContract.getIsInvoiceable() && !singNumValue.equals("-1") && irContract.getMaxAmount()!= null )
                    	{


								Money MATotalCostobj = (Money)irContract.getFieldValue("MaxAmount");
								if (MATotalCostobj !=null)
								{
								MATotalCostAmt =(BigDecimal)MATotalCostobj.getFieldValue("ApproxAmountInBaseCurrency");
								Log.customer.debug("MA Total Cost in Base Currency ***" +MATotalCostAmt);
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
																	 		totalMAIRAmt = queryResults.getBigDecimal(0);
																	 Log.customer.debug("Query MA 1 *** has result take the first value"  +totalMAIRAmt);
																	 if (totalMAIRAmt == null)
																	 	    {
																	 	   totalMAIRAmt = new BigDecimal(0.0);
																	Log.customer.debug("Query MA 1 *** No Receipts Processed hence total receipt amt is"  +totalMAIRAmt);

																	    }
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
				Log.customer.debug("Contract Not Receivable");
                    AmtCompareTo = MATotalCostAmt;

		}
            Amtleft = AmtCompareTo.subtract(totalMAIRAmt);
					Log.customer.debug("***AmtLeft ***" +Amtleft);

               BigDecimal Amtleft1 = null;
			   					  Amtleft1 = AmtCompareTo.subtract(totalMAIRAmt);
					  Log.customer.debug("Amt left over to compare without tolreance" +Amtleft1);


				   BigDecimal Tr = (BigDecimal) (Amtleft1.multiply(Tolrance));
				                    Log.customer.debug("Amt of Tolerance to be added" +Tr);
				                    Amtleft = Amtleft1.add(Tr);
		   Log.customer.debug("Final Amt after adding Tolerance" +Amtleft);


							Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Enter Else if of close contract Variance");


					       if (tcAmount.compareTo(Amtleft) > 0 && !singNumValue.equals("-1"))
							{

					        	Log.customer.debug("%s:: Not removing Contract Not Invoicing Variance as is fully received and invoiced",ClassName);

					        }
							else
                    		{

                            	//if (Log.customer.debugOn)

									Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Removing exception type: %s",MA_NOT_INVOICING );

                            	exceptions.remove(super.getInvoiceExceptionType(MA_NOT_INVOICING, ir.getPartition()));
                            	//if (Log.customer.debugOn)
									Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Successfully removed exception type: %s", MA_NOT_INVOICING);
							}
                    	}

						// If Credit Invoice, dont reject IR with 'Contract Not Invoicing' exception
						if(singNumValue.equals("-1"))
						{
							//if (Log.customer.debugOn)
								Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Removing exception type: %s",CLOSED_CONTRACT );
							exceptions.remove(super.getInvoiceExceptionType(CLOSED_CONTRACT,ir.getPartition()));
						}


			     }

			 	// ********************* CR189 MA Ends (Vikram J Singh) Ends *********************
		        }
		  }
                return exceptions;
        }

        /*
         *  Returns LineItem Exceptions
         *  ARajendren, Ariba Inc.,
         *	Changed the method signature from InvoiceReconciliationLineItem to StatementReconciliationLineItem
         *
         */
        protected List getExceptionTypesForLine(StatementReconciliationLineItem irli) {
                //if (Log.customer.debugOn)
                        Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: In method for generating exception type for line");
                List exceptions = super.getExceptionTypesForLine(irli);
//              for (int i = 0; i < ignoreExceptions.length; i++) {
                        if ((irli.getDottedFieldValue("LineItemCollection.taxCallNotFailed") == null)
                                || ((irli.getDottedFieldValue("LineItemCollection.taxCallNotFailed") != null)
                                        && ((Boolean) irli.getDottedFieldValue("LineItemCollection.taxCallNotFailed")).booleanValue())) {
								Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Removing exception type: %s", OVER_TAX);
                                exceptions.remove(super.getInvoiceExceptionType(OVER_TAX, irli.getPartition()));
                                Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Successfully removed exception type: %s", OVER_TAX);
                        }
                        if (!"001".equals(irli.getDottedFieldValue("Description.CAPSChargeCode.UniqueName"))){
                                //if (Log.customer.debugOn)
                                        Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Removing exception type: %s", PO_RCVD_Q_V);
                                exceptions.remove(super.getInvoiceExceptionType(PO_RCVD_Q_V, irli.getPartition()));
                                //if (Log.customer.debugOn)
                                        Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Successfully removed exception type: %s", PO_RCVD_Q_V);
                                //if (Log.customer.debugOn)
                                        Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Removing exception type: %s", MA_RCVD_Q_V);
                                exceptions.remove(super.getInvoiceExceptionType(MA_RCVD_Q_V, irli.getPartition()));
                                //if (Log.customer.debugOn)
                                        Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Successfully removed exception type: %s", MA_RCVD_Q_V);
                        }

                        //The unitprice check for tolerence
                        if (unitPriceWithinTolerence((InvoiceReconciliationLineItem)irli)){
                                Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Removing exception type: %s", PO_PRC_V);
                                exceptions.remove(super.getInvoiceExceptionType(PO_PRC_V, irli.getPartition()));
                                Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Successfully removed exception type: %s", PO_PRC_V);

                                Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Removing exception type: %s", PO_CATPRC_V);
                                exceptions.remove(super.getInvoiceExceptionType(PO_CATPRC_V, irli.getPartition()));
                                Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Successfully removed exception type: %s", PO_CATPRC_V);

                                Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Removing exception type: %s", MA_NC_PRC_V);
                                exceptions.remove(super.getInvoiceExceptionType(MA_NC_PRC_V, irli.getPartition()));
                                Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Successfully removed exception type: %s", MA_NC_PRC_V);

                                Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Removing exception type: %s", MA_CAT_PRC_V);
                                exceptions.remove(super.getInvoiceExceptionType(MA_CAT_PRC_V, irli.getPartition()));
                                Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Successfully removed exception type: %s", MA_CAT_PRC_V);
                        }

						// Logic to check credit invoices against Line level exceptions
						Money  totalCost;
						BigDecimal tcAmount;
						String singNumValue = "";

						ariba.contract.core.Contract irContract = (ariba.contract.core.Contract)irli.getMasterAgreement();

						if (irContract != null)
						{
							ariba.invoicing.core.Invoice irInv = (ariba.invoicing.core.Invoice)(((InvoiceReconciliationLineItem) irli).getInvoice());
							if (irInv!=null){

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
								Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Removing exception type: %s", MA_LINE_DATE_VAR);
                                exceptions.remove(super.getInvoiceExceptionType(MA_LINE_DATE_VAR, irli.getPartition()));
                                Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Successfully removed exception type: %s", MA_LINE_DATE_VAR);

							}
						}

//              }
                return exceptions;
        }

        /*
         *      Generate any custom Header Exceptions
         */
        private List generateHeaderExceptions(InvoiceReconciliation ir, List typesToValidate, List exceptions) {
                return exceptions;
        }

        /*
         *      Generate any custom Line Exceptions
         */
        private List generateLineExceptions(InvoiceReconciliationLineItem irli, List typesToValidate, List exceptions) {
                return exceptions;
        }

        private void defaultAccountingOnIRLineItems(Approvable approvable) {
                InvoiceReconciliation ir = (InvoiceReconciliation) approvable;

                if (!ir.getInvoice().isStandardInvoice()) {
                        return;
                }

                CatCSVDefaultAccountingOnIRLineItems.defaultAccountingOnLines(ir);

                BaseVector irLineItems = ir.getLineItems();
                InvoiceReconciliationLineItem irli = null;
                for (int i = 0; i < irLineItems.size(); i++) {
                        irli = (InvoiceReconciliationLineItem) irLineItems.get(i);
                        ClusterRoot capsChargeCodeObj = (ClusterRoot) irli.getFieldValue("CapsChargeCode");
                        String capsChargeCodeString = null;
                        if (capsChargeCodeObj != null) {
                                capsChargeCodeString = capsChargeCodeObj.getUniqueName();
                        }
                        else {
                                capsChargeCodeString = "";
                        }

                        if (irli.getLineType() != null){
                                if ((irli.getLineType().getCategory() != ProcureLineType.LineItemCategory)
                                        || ((irli.getLineType().getCategory() == ProcureLineType.LineItemCategory) && (!capsChargeCodeString.equals("001")))) {
                                        InvoiceLineItem invLineItem = irli.getInvoiceLineItem();
                                        if (invLineItem != null) {
                                            invLineItem.setAccountings(irli.getAccountings());
                                        }
                                }
                        }
                }
                ir.save();
        }

        private void checkIfIRToBeRejected(Approvable approvable) {
                //if (Log.customer.debugOn)
                        Log.customer.debug("%s ::: Entering the method checkIfIRToBeRejected", ClassName);
                InvoiceReconciliation ir = (InvoiceReconciliation) approvable;
                boolean isTaxLineLevel = false;

                if (!ir.getInvoice().isStandardInvoice()) {
                        return;
                }

                Invoice invoice = ir.getInvoice();
                BaseVector irLineItems = ir.getLineItems();
                InvoiceReconciliationLineItem irLineItem = null;

                if (invoice.getLoadedFrom() == Invoice.LoadedFromEForm || invoice.getLoadedFrom() == Invoice.LoadedFromUI) {
                        //if invoice was created using the invoice eform or invoice entry screen (paper invoice)
                        int numberOfTaxLines = 0;
                        int numberOfTaxDetails = 0;

                        for (int i = 0; i < irLineItems.size(); i++) {
                                irLineItem = (InvoiceReconciliationLineItem) irLineItems.get(i);

                                if (irLineItem.getLineType() != null && irLineItem.getLineType().getCategory() == ProcureLineType.TaxChargeCategory) {
//                                      if (irLineItem.getDottedFieldValue("MatchedLineItem") != null) {
//                                              isTaxLineLevel = true;
//                                      }
                                        numberOfTaxLines = numberOfTaxLines + 1;
                                }
                        }

                        numberOfTaxDetails = (ir.getTaxDetails()).size();

//                      if (isTaxLineLevel) {
//                              //if (Log.customer.debugOn)
//                                      Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Rejecting UI Invoice as line level tax");
//                              ir.reject();
//                              return;
//                      }

                        if ((numberOfTaxLines > 1) || (numberOfTaxDetails > 1)) {
                                //if (Log.customer.debugOn)
                                        Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Rejecting UI Invoice as more than one tax line");
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
                        boolean additionalLinesPresent = false;
                        boolean additionalSpecialChargePresent = false;
                        boolean headerSpecialCharge = false;
                        boolean invAgainstOIOProcessReq = false;

                        PurchaseOrder po = ir.getOrder();
                        if (po != null) {
                                //POLineItem poli = (POLineItem) po.getLineItems().get(0);
                                /*
                                String supplierLocUN = null;
                                if (po.getSupplierLocation() != null){
                                        supplierLocUN = po.getSupplierLocation().getUniqueName();
                                }
                                */
                                Object isOIOProcessReqB = po.getDottedFieldValue("OIOAgreement");
                                boolean isOIOProcessReq = false;
                                if (isOIOProcessReqB != null){
                                        isOIOProcessReq = ((Boolean)isOIOProcessReqB).booleanValue();
                                }
                                //if (supplierLocUN!=null && "C2986X0".equals(supplierLocUN) && isOIOProcessReq){
                                if (isOIOProcessReq){
                                        //if (Log.customer.debugOn)
                                                Log.customer.debug("%s ::: Invoice against IBM OIO Requisition %s", ClassName, ir.getUniqueName());
                                        invAgainstOIOProcessReq = isOIOProcessReq;
                                }
                        }

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

                        for (int i = 0; i < irLineItems.size(); i++) {
                                irLineItem = (InvoiceReconciliationLineItem) irLineItems.get(i);

                                if(irLineItem.getLineType() != null){
                                        if ((irLineItem.getLineType().getCategory() == ProcureLineType.FreightChargeCategory)
                                                || (irLineItem.getLineType().getCategory() == ProcureLineType.DiscountCategory)
                                                || (irLineItem.getLineType().getCategory() == ProcureLineType.HandlingChargeCategory)) {
                                                        //if ( !( ( (java.lang.String)invLineItem.getDottedFieldValue("SupplierLocation.UniqueName") ).equals("AD6673") && (invLineItem.getLineType().getCategory() == ProcureLineType.FreightChargeCategory) ) )
                                                        //Code for Dell Freight Charges Exception
                                                        String supplierId = null;
                                                        if (irLineItem.getSupplier() != null)
                                                        {
                                                                supplierId = irLineItem.getSupplier().getUniqueName();
                                                        }
                                                        else
                                                        {
                                                                ir.reject();
                                                                return;
                                                        }

                                                        //if (!((((java.lang.String)irLineItem.getDottedFieldValue("SupplierLocation.UniqueName")).equals("M0162H0") ||
                                                        //((java.lang.String)irLineItem.getDottedFieldValue("SupplierLocation.UniqueName")).equals("C2986X0") ||
                                                        //((java.lang.String)irLineItem.getDottedFieldValue("SupplierLocation.UniqueName")).equals("V1160T0"))
                                                        //&& (irLineItem.getLineType().getCategory() == ProcureLineType.FreightChargeCategory) ) )

                                                        if ( (StringUtil.occurs(freightSupplierArr, supplierId))==0
                                                                        && (irLineItem.getLineType().getCategory() == ProcureLineType.FreightChargeCategory) ) {
                                                                Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Rejecting Electronic Invoice for freight from other supplier");
                                                                additionalAC = additionalAC + 1;
                                                        }
                                        }
                                        if ((irLineItem.getLineType().getCategory() == ProcureLineType.SpecialChargeCategory)) {
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

                                                    // the concept in 9r for line level tax is different.. modified logic
                                                ProcureLineItem pli = irLineItem.getParent();
                                                if (pli != null && pli instanceof InvoiceReconciliationLineItem) {
                                                    if (pli.getNumberInCollection() > 0) {
                                                        isTaxLineLevel = true;
                                                    }
                                                }
                                                numberOfTaxLines = numberOfTaxLines + 1;
                                        }
                                        if (irLineItem.getLineType().getCategory() == ProcureLineType.LineItemCategory) {
                                                if ((irLineItem.getFieldValue("OrderLineItem") == null) && (irLineItem.getFieldValue("MALineItem") == null)) {
                                                        additionalLinesPresent = true;
                                                }
                                        }
                                }
                        }

                        if (invAgainstOIOProcessReq){
                                //if (Log.customer.debugOn)
                                        Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Rejecting Electronic Invoice as Invoice against IBM OIO Requisition");
                                String rejectionMessage = "Caterpillar does not accept Invoices against the IBM OIO Process Requisitions";
                                LongString commentText = new LongString(rejectionMessage);
                                String commentTitle = "Reason For Invoice Rejection";
                                Date commentDate = new Date();
                                User commentUser = User.getAribaSystemUser(ir.getPartition());
                                CatTaxUtil.addCommentToIR(ir, commentText, commentTitle, commentDate, commentUser);
                                ir.reject();
                                return;
                        }

                        if (headerSpecialCharge){
                                //if (Log.customer.debugOn)
                                        Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Rejecting Electronic Invoice as Header Special Charge");
                                String rejectionMessage = "Caterpillar does not accept Special Charge charged at header level";
                                LongString commentText = new LongString(rejectionMessage);
                                String commentTitle = "Reason For Invoice Rejection";
                                Date commentDate = new Date();
                                User commentUser = User.getAribaSystemUser(ir.getPartition());
                                CatTaxUtil.addCommentToIR(ir, commentText, commentTitle, commentDate, commentUser);
                                ir.reject();
                                return;
                        }

                        if (additionalLinesPresent) {
                                //if (Log.customer.debugOn)
                                        Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Rejecting Electronic Invoice as Additional Material Line");
                                String rejectionMessage = "Caterpillar does not accept invoices with additional material lines than from the PO";
                                LongString commentText = new LongString(rejectionMessage);
                                String commentTitle = "Reason For Invoice Rejection";
                                Date commentDate = new Date();
                                User commentUser = User.getAribaSystemUser(ir.getPartition());
                                CatTaxUtil.addCommentToIR(ir, commentText, commentTitle, commentDate, commentUser);
                                ir.reject();
                                return;
                        }
/*
                        if (additionalSpecialChargePresent && !isSUTPresent) {
                                //if (Log.customer.debugOn)
                                        Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Rejecting Electronic Invoice as Special Charge Line without SUT");
                                String rejectionMessage = "Caterpillar does not accept line level Special Charges without Sales Tax or Use Tax charged on the invoice";
                                LongString commentText = new LongString(rejectionMessage);
                                String commentTitle = "Reason For Invoice Rejection";
                                Date commentDate = new Date();
                                User commentUser = User.getAribaSystemUser(ir.getPartition());
                                CatTaxUtil.addCommentToIR(ir, commentText, commentTitle, commentDate, commentUser);
                                ir.reject();
                                return;
                        }
*/
                        numberOfTaxDetails = (ir.getTaxDetails()).size();
                        // Start: Mach1 R5.5 (FRD1.5/TD1.3)
                       /* if (isTaxLineLevel) {
                                //if (Log.customer.debugOn)
                                        Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Rejecting Electronic Invoice as line level tax");
                                String rejectionMessage = "Caterpillar does not accept line level taxes";
                                LongString commentText = new LongString(rejectionMessage);
                                String commentTitle = "Reason For Invoice Rejection";
                                Date commentDate = new Date();
                                User commentUser = User.getAribaSystemUser(ir.getPartition());
                                CatTaxUtil.addCommentToIR(ir, commentText, commentTitle, commentDate, commentUser);
                                ir.reject();
                                return;
                        }*/
                        // End: Mach1 R5.5 (FRD1.5/TD1.3)
                        if (additionalAC > 0) {
                                //if (Log.customer.debugOn)
                                        Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Rejecting Electronic Invoice as an Additional Charge identified");
                                String rejectionMessage = "Caterpillar does not accept Freight, Discount or Handling charges on Invoice";
                                LongString commentText = new LongString(rejectionMessage);
                                String commentTitle = "Reason For Invoice Rejection";
                                Date commentDate = new Date();
                                User commentUser = User.getAribaSystemUser(ir.getPartition());
                                CatTaxUtil.addCommentToIR(ir, commentText, commentTitle, commentDate, commentUser);
                                ir.reject();
                                return;
                        }
                        // End: Mach1 R5.5 (FRD1.5/TD1.3)
                 /*       if ((numberOfTaxLines > 1) || (numberOfTaxDetails > 1)) {
                                //if (Log.customer.debugOn)
                                        Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Rejecting Electronic Invoice as more than one tax line");
                                String rejectionMessage = "Caterpillar does not accept multiple taxes charged on an Invoice";
                                LongString commentText = new LongString(rejectionMessage);
                                String commentTitle = "Reason For Invoice Rejection";
                                Date commentDate = new Date();
                                User commentUser = User.getAribaSystemUser(ir.getPartition());
                                CatTaxUtil.addCommentToIR(ir, commentText, commentTitle, commentDate, commentUser);
                                ir.reject();
                                return;
                        }*/
						// End: Mach1 R5.5 (FRD1.5/TD1.3)
                        if (!CatCSVInvoiceSubmitHook.vatReasonablnessCheck(irLineItems)){
                                //if (Log.customer.debugOn)
                                        Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Rejecting Electronic Invoice as VAT charged > 50%");
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

        private void copyFieldsFromInvoice(Approvable approvable) {
                //if (Log.customer.debugOn)
                        Log.customer.debug("%s ::: Entering the method copyFieldsFromInvoice", ClassName);
                InvoiceReconciliation ir = (InvoiceReconciliation) approvable;
                Invoice invoice = ir.getInvoice();

                BigDecimal betterTermsDisc = getBetterTermsDiscount(ir);
                betterTermsDisc = betterTermsDisc.setScale(2, BigDecimal.ROUND_UP);

                ir.setFieldValue("BlockStampDate", (Date) invoice.getFieldValue("BlockStampDate"));
                ir.setFieldValue("TermsDiscount", betterTermsDisc);
                ir.setFieldValue("SettlementCode", (ClusterRoot) invoice.getFieldValue("SettlementCode"));

                BaseVector irLineItems = ir.getLineItems();
                InvoiceReconciliationLineItem irli = null;
                InvoiceLineItem invli = null;
                for (int i = 0; i < irLineItems.size(); i++) {
                        irli = (InvoiceReconciliationLineItem) irLineItems.get(i);
                        invli = irli.getInvoiceLineItem();
                        //Changed the field definition from String to BigDecimal for CAPSIntegration
                        irli.setFieldValue("TermsDiscountPercent", betterTermsDisc);
                        irli.setFieldValue("SettlementCode", (ClusterRoot) invoice.getFieldValue("SettlementCode"));
                        irli.setDottedFieldValue("TaxCodeOverride", invli.getDottedFieldValue("TaxCodeOverride"));
                        irli.setDottedFieldValue("TaxAllFieldsOverride", invli.getDottedFieldValue("TaxAllFieldsOverride"));
                }
        }

        public static BigDecimal getBetterTermsDiscount(InvoiceReconciliation ir) {
                //if (Log.customer.debugOn)
                        Log.customer.debug("%s ::: Entering the method getBetterTermsDiscount", ClassName);
                Invoice invoice = ir.getInvoice();
                BigDecimal exemptSuppDisc = new BigDecimal("0.5");
                BigDecimal globalMinDisc = new BigDecimal("1.25");
                boolean useSupTerms = false;
                boolean useInvTerms = false;
                boolean isSupExempt = false;

                BigDecimal invTermsDisc = (BigDecimal) invoice.getFieldValue("TermsDiscount");
                if (invTermsDisc == null) {
                        invTermsDisc = new BigDecimal("0.00");
                }
                //if (Log.customer.debugOn)
                        Log.customer.debug("%s ::: The terms discount on the invoice is %s", ClassName, invTermsDisc.toString());

                if (invoice.getDottedFieldValue("Supplier.Exempt") != null) {
                        isSupExempt = ((Boolean) invoice.getDottedFieldValue("Supplier.ExemptSupplier")).booleanValue();
                }
                //if (Log.customer.debugOn)
                        Log.customer.debug("%s ::: The Exempt Status of Supplier is: " + isSupExempt, ClassName);

                String supTermsDiscStrg = (String) invoice.getDottedFieldValue("SupplierLocation.DiscountPercent");
                BigDecimal supTermsDisc = new BigDecimal("0.00");
                if (supTermsDiscStrg != null) {
                        supTermsDisc = new BigDecimal(supTermsDiscStrg);
                        supTermsDisc = supTermsDisc.setScale(2, BigDecimal.ROUND_UP);
                }
                //if (Log.customer.debugOn)
                        Log.customer.debug("%s ::: The Supplier terms discount is %s", ClassName, supTermsDisc.toString());

                if (invTermsDisc.compareTo(Constants.ZeroBigDecimal) > 0) {
                        if (invTermsDisc.compareTo(supTermsDisc) >= 0) {
                                if (invTermsDisc.compareTo(globalMinDisc) >= 0) {
                                        useInvTerms = true;
                                }
                                else {
                                        if (isSupExempt) {
                                                if (invTermsDisc.compareTo(exemptSuppDisc) >= 0) {
                                                        useInvTerms = true;
                                                }
                                        }
                                }
                        }
                        else {
                                useSupTerms = true;
                        }
                }
                else {
                        useSupTerms = true;
                }

                if (useSupTerms) {
                        //if (Log.customer.debugOn)
                                Log.customer.debug("%s ::: Returning the Supplier Terms Discount", ClassName);
                        return supTermsDisc;
                }
                else if (useInvTerms) {
                        //if (Log.customer.debugOn)
                                Log.customer.debug("%s ::: Returning the Invoice Terms Discount", ClassName);
                        return invTermsDisc;
                }
                else {
                        //if (Log.customer.debugOn)
                                Log.customer.debug("%s ::: Returning the Zero Terms Discount", ClassName);
                        return Constants.ZeroBigDecimal;
                }
        }

        public static void calculateDiscountedAmounts(Approvable approvable) {
                //if (Log.customer.debugOn)
                        Log.customer.debug("%s ::: Entering the method calculateDiscountedAmounts", ClassName);
                InvoiceReconciliation ir = (InvoiceReconciliation) approvable;
                BigDecimal discPercnt = (BigDecimal) ir.getFieldValue("TermsDiscount");
                discPercnt = discPercnt.setScale(5);
                BigDecimal discPercntMultiplier = discPercnt.divide(new BigDecimal("100.00000"),5, BigDecimal.ROUND_HALF_UP);

                //if (Log.customer.debugOn) {
                        Log.customer.debug("%s ::: The discPercent value is: %s", ClassName, discPercnt.toString());
                        Log.customer.debug("%s ::: The discPercentMultiplier value is: %s", ClassName, discPercntMultiplier.toString());
                //}

                ClusterRoot capsChargeCodeObj = null;
                boolean isDiscountable = false;

                Money liAmount = new Money();
                Money saAmount = new Money();
                Money liDiscAmount = new Money();
                Money saDiscAmount = new Money();
                Money liMinusDiscAmount = new Money();
                Money saMinusDiscAmount = new Money();

                BaseVector irLineItems = ir.getLineItems();
                InvoiceReconciliationLineItem irli = null;
                for (int i = 0; i < irLineItems.size(); i++) {
                        irli = (InvoiceReconciliationLineItem) irLineItems.get(i);
                        liAmount = irli.getAmount();

                        capsChargeCodeObj = (ClusterRoot) irli.getFieldValue("CapsChargeCode");
                        if (capsChargeCodeObj != null) {
                                isDiscountable = ((Boolean) capsChargeCodeObj.getFieldValue("Discountable")).booleanValue();
                        }
                        //if (Log.customer.debugOn)
                                Log.customer.debug("%s ::: IR Line Item " + irli.getNumberInCollection() + " is Discountable: " + isDiscountable, ClassName);

                        SplitAccountingCollection sac = irli.getAccountings();

                        for(int j=0; j<sac.getSplitAccountings().size(); j++){
                                SplitAccounting sa = (SplitAccounting) sac.getSplitAccountings().get(j);
                                saAmount = sa.getAmount();

                                saDiscAmount = new Money(new BigDecimal("0.00"), saAmount.getCurrency());
                                if (isDiscountable) {
                                        saDiscAmount = saAmount.multiply(discPercntMultiplier);
                                }
                                saMinusDiscAmount = Money.subtract(saAmount, saDiscAmount);

                                //if (Log.customer.debugOn) {
                                        Log.customer.debug("%s ::: The Discount dollar amount is: %s", ClassName, saDiscAmount.toString());
                                        Log.customer.debug("%s ::: The Discounted LI dollar amount is: %s", ClassName, saMinusDiscAmount.toString());
                                //}

                                BigDecimal saDiscAmountBD = saDiscAmount.getAmount();
                                saDiscAmountBD = saDiscAmountBD.setScale(2,BigDecimal.ROUND_DOWN);
                                saDiscAmount = new Money(saDiscAmountBD, saAmount.getCurrency());

                                //if (Log.customer.debugOn) {
                                        Log.customer.debug("%s ::: The Discount dollar amount after setScale() is: %s", ClassName, saDiscAmount.toString());
                                //}

                                sa.setDottedFieldValue("InvoiceSplitDiscountDollarAmount", saDiscAmount);
                        }

                        liDiscAmount = new Money(new BigDecimal("0.00"), liAmount.getCurrency());
                        if (isDiscountable) {
                                liDiscAmount = liAmount.multiply(discPercntMultiplier);
                        }
                        else{
                                //if (Log.customer.debugOn) {
                                        Log.customer.debug("%s ::: Setting the TermsDiscountPercent to %s as line is not discountable", liDiscAmount.getAmount().toString());
                                //}
                                irli.setDottedFieldValue("TermsDiscountPercent", liDiscAmount.getAmount());
                        }

                        liMinusDiscAmount = Money.subtract(liAmount, liDiscAmount);

                        //if (Log.customer.debugOn) {
                                Log.customer.debug("%s ::: The Discount dollar amount is: %s", ClassName, liDiscAmount.toString());
                                Log.customer.debug("%s ::: The Discounted LI dollar amount is: %s", ClassName, liMinusDiscAmount.toString());
                        //}

                        BigDecimal liMinusDiscAmountBD = liMinusDiscAmount.getAmount();
                        liMinusDiscAmountBD = liMinusDiscAmountBD.setScale(2,BigDecimal.ROUND_DOWN);
                        liMinusDiscAmount = new Money(liMinusDiscAmountBD, liAmount.getCurrency());

                        //if (Log.customer.debugOn) {
                                Log.customer.debug("%s ::: The Discounted LI dollar amount after setScale() is: %s", ClassName, liMinusDiscAmount.toString());
                        //}

                        //irli.setDottedFieldValue("TotalInvoiceLineDiscountDollarAmount", liDiscAmount);
                        irli.setDottedFieldValue("DiscountedLIDollarAmount", liMinusDiscAmount);
                }
        }

        private boolean unitPriceWithinTolerence(InvoiceReconciliationLineItem irli) {
                //Check if unit price is within defined tolerence.
                //Eg. unit price to be 1% of order line unit price and line amount total not to exceed 10 dollars
            //unitPriceMaxTolerencePct = BigDecimalFormatter.round(unitPriceMaxTolerencePct, 2);
        //unitPriceMinTolerencePct = BigDecimalFormatter.getBigDecimalValue((100.00 - unitPriceTolerencePct)/100.00));
                try {
                        unitPriceTolerencePctDouble = DoubleFormatter.parseDouble(UnitPriceTolerance);
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

                if (orderOrMALineUnitPrice == null) return false;
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

                Log.customer.debug("%s :::delta="+delta.toString() +" amtTolerenceMoney="+amtTolerenceMoney.toString(), ClassName);

                if (delta.compareTo(amtTolerenceMoney) > 0 ) {
                        Log.customer.debug("%s :::returning false as the line amt is outside tolerence", ClassName);
                        return false;
                }
                return true;
        }

    protected boolean validateBody(Approvable approvable) {
        Assert.that(approvable instanceof InvoiceReconciliation, "%s.validateBody: approvable must be an IR", "CatCSVInvoiceReconciliationEngine");
        Log.customer.debug("CatCSVInvoiceReconciliationEngine.validateBody called with %s", approvable);
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
                    // S.Sato AUL - commented this section as we could do without
                    // validating this twice

                    // return super.validateBody(approvable);
                return anyExceptions;
            }

            Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: freight line exception and supplierid= %s", supplierId);

            Iterator exceptions = li.getExceptionsIterator();
            while(exceptions.hasNext()) {
                InvoiceException invException = (InvoiceException)exceptions.next();

                if(invException != null
                                        && invException.getType().getUniqueName().equals(FREIGHT_VARIANCE)
                                        && !StringUtil.nullOrEmptyOrBlankString(supplierId) ) {

                                                Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: freightSupplierArr=**"+ ListUtil.listToString(ListUtil.arrayToList(freightSupplierArr), "**"));

                                                if((StringUtil.occurs(freightSupplierArr, supplierId))>0){
                                                        invException.setState(ReconciliationException.Accepted);
                                                        Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Accepting the freight variance");
                                                } else {
                                                        Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: Disputing the freight variance");
                                                        invException.setState(ReconciliationException.Disputed);
                                                }

                                        }//freight inv exception
                        }// end exception iterator
        } //end line iterator

            // S.Sato AUL - commented this section as we could do without
            // validating this twice
            // return super.validateBody(approvable);
        return anyExceptions;
    }
}
