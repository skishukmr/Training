/*
 Author: Nani Venkatesan (Ariba Inc.)
   Date; 5/29/2005
Purpose: The purpose of this class is to provide variant specfic invoice reconciliation behaviour. Some of them
         are following:
         1. Defaults accounting information on additional charge lines
         2. Overrides the payment terms on the IR to that of the order or MA.
         3. Overrides the OOB way of handling absolute tolerance for exceptions such as
            a. POLineAmountVariance
            b. POPriceVariance
            c. POCatalogPriceVariance
            d. POLineReceivedAmountVariance and
            e. POAmountVariance
2.		Kingshuk Mazumdar		09/26/2006		Adding the auto-rejection logic based on VAT percent value

3. V Chacko -  Commented out some Header Level exceptions 	- do not want them to appear.											for ASN invoices

4. Dibya Prakash --- 25/11/2008  Issue 878 : Code changed for UK VAT (changing from 17.5 to 15)

5. Vikram J Singh  25-06-2009    Issue 961 :   Issue Description: Adding Close PO functionality by Close Order eForm.
											   Enabling Close Order Date for Orders.
											   IR Header Level Exceptions - ClosePOVariance and CancelPOVariance.
											   Indirect Buyer, Requisitioner to handle ClosePOVariance, CancelPOVariance resp.

Ashwini Jan 06,2019 - Code changed for UK VAT (changing from 15to 17.5)	(issue number _1026)

Vikram J Singh		29-09-2011		Setting VATClass and TaxAmount on Invoice and IR line Items

Manoj R             28-01-2012   Copying ExchangeRate value from Invoice using TaxExchangeRate Field(WorkItem 243).

04/12/2012      IBM AMS_Lekshmi   WI 241          Auto Rejecting InvoiceReconciliation if the Order/Contract Currency different from Invoice Currency

08/08/2012      IBM AMS_Manoj     WI 295          Close Order variance is added based on the value of Closed field.

17/10/2013      IBM_AMS_Kalikumar RSD             Fixing Exchange rate not getting populated on IRs Issue.

 */

package config.java.invoicing.vcsv2;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;

import ariba.approvable.core.Approvable;
import ariba.base.core.Base;
import ariba.base.core.BaseObject;
import ariba.base.core.BaseVector;
import ariba.base.core.ClusterRoot;
import ariba.base.core.LongString;
import ariba.basic.core.Currency;
import ariba.basic.core.Money;
import ariba.contract.core.Contract;
import ariba.invoicing.core.Invoice;
import ariba.invoicing.core.InvoiceException;
import ariba.invoicing.core.InvoiceExceptionType;
import ariba.invoicing.core.InvoiceLineItem;
import ariba.invoicing.core.InvoiceReconciliation;
import ariba.invoicing.core.InvoiceReconciliationLineItem;
import ariba.invoicing.core.Log;
import ariba.procure.core.ProcureLineType;
import ariba.purchasing.core.PurchaseOrder;
import ariba.reconciliation.core.ReconciliationException;
import ariba.statement.core.StatementReconciliation;
import ariba.statement.core.StatementReconciliationLineItem;
import ariba.tax.core.TaxDetail;
import ariba.user.core.User;
import ariba.util.core.Assert;
import ariba.util.core.Date;
import ariba.util.core.Fmt;
import ariba.util.core.ResourceService;
import ariba.util.formatter.BigDecimalFormatter;
import config.java.invoicing.CatInvoiceReconciliationEngine;
import config.java.tax.CatTaxUtil;

public class CatMFGInvoiceReconciliationEngine extends
		CatInvoiceReconciliationEngine {

	public static final String ClassName = "config.java.invoicing.vcsv2.CatMFGInvoiceReconciliationEngine";
	public static final String ComponentStringTable = "cat.java.vcsv1.csv";
	private static final String LINE_AMOUNT = "POLineAmountVariance";
	private static final String PO_PRICE = "POPriceVariance";
	private static final String PO_CATALOG_PRICE = "POCatalogPriceVariance";
	private static final String RECEIVED_AMOUNT = "POLineReceivedAmountVariance";
	private static final String PO_AMOUNT = "POAmountVariance";
	// Issue 961 -- Added CLOSE_ORDER & CANCELLED_ORDER variables
	private static final String CLOSE_ORDER = "ClosePOVariance";
	private static final String CANCELLED_ORDER = "CancelledPOVariance";
	private static final String VAT_RATE_CHANGED = Fmt.Sil("cat.java.vcsv2",
			"VatRateChanges");

	private static final int PO_PRICE_ABSOLUTE_RANK = 3,
			PO_AMOUNT_ABSOLUTE_RANK = 10;

	private String[] lineExcTypes = { PO_CATALOG_PRICE, PO_PRICE, LINE_AMOUNT,
			RECEIVED_AMOUNT };

	private static final BigDecimal OneBigDecimal = new BigDecimal("1");
	private static final BigDecimal OneHundredBigDecimal = new BigDecimal("100");

	public boolean reconcile(Approvable approvable) {
		if (approvable instanceof InvoiceReconciliation) {
			defaultAccountingOnIRLineLevelAdditionalCharges(approvable);
		}

		return super.reconcile(approvable);
	}

	protected boolean validateHeader(Approvable approvable) {

		Assert
				.that(approvable instanceof InvoiceReconciliation,
						"%s.validateHeader: approvable must be an IR",
						"config.java.invoicing.vcsv2.CatMFGInvoiceReconciliationEngine");
		InvoiceReconciliation ir = (InvoiceReconciliation) approvable;
		Log.customer
				.debug(
						"%s.validateHeader called with %s",
						"config.java.invoicing.vcsv2.CatMFGInvoiceReconciliationEngine",
						ir);
		// set payment terms on the IR header from the first PO or MA.
		Invoice inv = ir.getInvoice();
              // Fixing Exchange Rate Issue
		if (inv != null) {
			// copy the exchange rate from invoice to IR
			Log.customer.debug("Entering into Exchange Rate Loop");
			if (inv.getDottedFieldValue("ExchangeRate") != null)
			{
			ir.setDottedFieldValue("ExchangeRate",(BigDecimal) inv.getDottedFieldValue("ExchangeRate"));
                        Log.customer.debug("ExchangeRate "+ (inv.getDottedFieldValue("ExchangeRate")));
                        Log.customer.debug("TaxExchangeRate "+ (inv.getDottedFieldValue("TaxExchangeRate")));
			}
		        else if (inv.getTaxExchangeRate() !=null && inv.getTaxExchangeRate().compareTo(BigDecimal.ZERO) > 0)
			{
                         Log.customer.debug("Entering into second loop");
			ir.setDottedFieldValue("ExchangeRate", (BigDecimal) inv
					.getTaxExchangeRate()); // WI 243 by Manoj
			}

			else

			{
                                Log.customer.debug("Entering into last loop");
				ir.setDottedFieldValue("ExchangeRate",null);
	 	    }
		}
            // End of Exchange rate Issue

		if (inv.isStandardInvoice()) {
			BaseVector v = inv.getLineItems();
			if (v.size() > 0) {
				InvoiceLineItem ili = (InvoiceLineItem) v.get(0);

				PurchaseOrder po = ili.getOrder();
				if (po != null) {
					Log.customer.debug("Get payment terms from the PO "
							+ po.getUniqueName());
					ir.setPaymentTerms(po.getPaymentTerms());
				} else {
					Contract ma = ili.getMasterAgreement();
					if (ma != null) {
						Log.customer.debug("Get payment terms from the MA "
								+ ma.getUniqueName());
						ir.setPaymentTerms(ma.getPaymentTerms());
					}
				}
			} else {
				Log.customer.debug("No line items on the invoice "
						+ inv.getUniqueName());
			}

		}
		// Start Of A4 By Kingshuk
		checkIfIRToBeRejected(ir);
		// End Of A4 By Kingshuk
		// Start Of Issue 241
		autoRejectIfInvoiceCurrencyDiffFromOrderOrContract(ir);
		// issue 241 End
		return super.validateHeader(approvable);

	}

	/*
	 * Auto Rejecting IR if Invoice Currency is different from the Order or
	 * Contract Currency from which it got created
	 */

	private void autoRejectIfInvoiceCurrencyDiffFromOrderOrContract(
			Approvable approvable) {
		Log.customer
				.debug("Entering In Method autoRejectIfInvoiceCurrencyDiffFromOrderOrContract");
		Assert
				.that(approvable instanceof InvoiceReconciliation,
						"%s.validateHeader: approvable must be an IR",
						"config.java.invoicing.vcsv2.CatMFGInvoiceReconciliationEngine");
		InvoiceReconciliation ir = (InvoiceReconciliation) approvable;
		if (ir.getTotalCost() != null) {
			Currency irCurrency = ir.getTotalCost().getCurrency();
			boolean flag = false;
			BaseObject bo = ir.getOrder();
			if (bo == null) {
				bo = ir.getMasterAgreement();
			}
			if (bo != null) {
				if (bo instanceof PurchaseOrder) {
					Log.customer.debug("Bo is instance of Purchase Order");
					PurchaseOrder po = (PurchaseOrder) bo;
					try {
						Currency orderCurrency = po.getTotalCost()
								.getCurrency();
						if (!irCurrency.equals(orderCurrency)) {
							flag = true;
						}
					} catch (NullPointerException npe) {
						Log.customer
								.debug("Null Pointer Exception occured while auto rejecting Invoice ");
					}
				} else if (bo instanceof Contract) {
					Log.customer.debug("Bo is instance of Contract");
					Contract contract = (Contract) bo;
					Currency contractCurrency = contract.getTotalCost()
							.getCurrency();
					if (!irCurrency.equals(contractCurrency)) {
						flag = true;
					}
				}
				Log.customer.debug("isDiff Value is %s", flag);
				if (flag == true) {
					Log.customer.debug("Adding Comments to show the reason");
					String rejectionMessage = "Caterpillar Rejects Invoice whose Currency is different from the Contract or Order Currency from which its created";
					LongString commentText = new LongString(rejectionMessage);
					String commentTitle = "Reason For Invoice Rejection";
					Date commentDate = new Date();
					User commentUser = User.getAribaSystemUser(ir.getPartition());
					CatTaxUtil.addCommentToIR(ir, commentText, commentTitle, commentDate, commentUser);
					Log.customer.debug("IR is Auto Rejected");
					ir.reject();
				}
			}
		}
		Log.customer
				.debug("Exit from Method autoRejectIfInvoiceCurrencyDiffFromOrderOrContract");
	}

	protected List generateExceptions(BaseObject parent, List typesToValidate) {
		Log.customer.debug(ClassName + " generateExceptions..: " + parent);
		List exceptions = super.generateExceptions(parent, typesToValidate);
		if (parent instanceof InvoiceReconciliation) {
			exceptions = generateHeaderExceptions(
					(InvoiceReconciliation) parent, typesToValidate, exceptions);
		} else {
			exceptions = generateLineExceptions(
					(InvoiceReconciliationLineItem) parent, typesToValidate,
					exceptions);
		}
		return exceptions;
	}

	/*
	 * Returns Header Exceptions - remove all Amount variance exceptions Amount
	 * variances will be manually generated ARajendren, Ariba Inc., Changed the
	 * method signature from InvoiceReconciliation to StatementReconciliation
	 */

	protected List getExceptionTypesForHeader(StatementReconciliation ir) {
		List exceptions = super.getExceptionTypesForHeader(ir);
		if (!ir.getConsolidated()) {
			exceptions.remove(super.getInvoiceExceptionType(PO_AMOUNT, ir
					.getPartition()));
		}

		/* *************Issue 961 -- Code for ClosePOVariance
		// -- Code Starts*************
		if (ir.getFieldValue("Order") != null) {
			// if (Log.customer.debugOn)
			Log.customer
					.debug(
							"CatCSVInvoiceReconciliationEngine ::: CloseOrder Value IS: %s...",
							(Boolean) ir
									.getDottedFieldValue("Order.CloseOrder"));
			if ((ir.getDottedFieldValue("Order.CloseOrder") != null)
					&& (((Boolean) ir.getDottedFieldValue("Order.CloseOrder"))
							.booleanValue())) {
				// if (Log.customer.debugOn)
				Log.customer
						.debug(
								"CatCSVInvoiceReconciliationEngine ::: CloseOrder Value IS: %s... Hence NOT removing the %s exception",
								(Boolean) ir
										.getDottedFieldValue("Order.CloseOrder"),
								CLOSE_ORDER);
			} else {
				// if (Log.customer.debugOn)
				Log.customer
						.debug(
								"CatCSVInvoiceReconciliationEngine ::: Removing exception type: %s",
								CLOSE_ORDER);
				exceptions.remove(super.getInvoiceExceptionType(CLOSE_ORDER, ir
						.getPartition()));
				// if (Log.customer.debugOn)
				Log.customer
						.debug(
								"CatCSVInvoiceReconciliationEngine ::: Successfully removed exception type: %s",
								CLOSE_ORDER);
			}
                    */
                    //  ***** WI 295 Starts ********
		     /* Check for Close Order Variance based on "Closed" field
		      * If Closed = 1 , Order is Open
		      * If Closed = 4 , Order is closed for invoicing and if Closed = 5 , Order is closed for all.
		      */
		         if (ir.getFieldValue("Order") != null)
		          {
					 ariba.purchasing.core.PurchaseOrder irOrder = (ariba.purchasing.core.PurchaseOrder)ir.getOrder();
		  			 Integer closeOrder = (Integer) irOrder.getFieldValue("Closed");
		  			 int closeState = closeOrder.intValue();
		  			 Log.customer.debug("%s::: CloseOrder Value IS: %s...",ClassName,closeOrder);
		  			 if ((closeState == 4 || closeState == 5))
		  			 {
		  				//if (Log.customer.debugOn)
		  			    		Log.customer.debug("CatCSVInvoiceReconciliationEngine ::: CloseOrder Value IS: %s... Hence NOT removing the %s exception", (Boolean)ir.getDottedFieldValue("Order.CloseOrder"), CLOSE_ORDER);
		  		         }
		  		         else
		  		         {
		  		                Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Removing exception type: %s", CLOSE_ORDER);
		  				exceptions.remove(super.getInvoiceExceptionType(CLOSE_ORDER, ir.getPartition()));
		  				Log.customer.debug("CatSAPInvoiceReconciliationEngine ::: Successfully removed exception type: %s", CLOSE_ORDER);
		  			  }

                 // ******* WI 295 Ends *********

                    // *************Issue 961 -- Code for  CancelPOVariance
			Integer orderedState = (Integer) ir
					.getDottedFieldValue("Order.OrderedState");
			int orderStatetemp = orderedState.intValue();

			// if (Log.customer.debugOn)
			Log.customer
					.debug("CatCSVInvoiceReconciliationEngine ::: CancelledOrder Code");
			if ((ir.getDottedFieldValue("Order.OrderedState") != null)
					&& (orderStatetemp == 16)) {
				// if (Log.customer.debugOn)
				Log.customer
						.debug(
								"CatCSVInvoiceReconciliationEngine ::: CancelledOrder Value IS: %s... Hence NOT removing the %s exception",
								ir.getDottedFieldValue("Order.OrderedState"),
								CANCELLED_ORDER);
			}

			else {
				// if (Log.customer.debugOn)
				Log.customer
						.debug(
								"CatCSVInvoiceReconciliationEngine ::: Removing exception type: %s",
								CANCELLED_ORDER);
				exceptions.remove(super.getInvoiceExceptionType(
						CANCELLED_ORDER, ir.getPartition()));
				// if (Log.customer.debugOn)
				Log.customer
						.debug(
								"CatCSVInvoiceReconciliationEngine ::: Successfully removed exception type: %s",
								CANCELLED_ORDER);
			}
	 }
		// *************Issue 961 -- Code for ClosePOVariance & CancelPOVariance
		// -- Code Ends*************

		return exceptions;
	}

	/*
	 * Returns LineItem Exceptions - remove all PO related amount variance
	 * exceptions PO amount variances will be manually generated. Credit Lines
	 * will not check for Receipt Quantity Variances ARajendren, Ariba Inc.,
	 * Changed the method signature from InvoiceReconciliationLineItem to
	 * StatementReconciliationLineItem
	 */

	protected List getExceptionTypesForLine(StatementReconciliationLineItem irli) {

		List exceptions = super.getExceptionTypesForLine(irli);

		for (int i = 0; i < lineExcTypes.length; i++) {
			exceptions.remove(super.getInvoiceExceptionType(lineExcTypes[i],
					irli.getPartition()));
		}

		return exceptions;

	}

	/**
	 * Generate any custom Header Exceptions
	 */

	/* Vijayan modified Header exceptions */
	private List generateHeaderExceptions(InvoiceReconciliation ir,
			List typesToValidate, List exceptions) {
		Log.customer.debug(ClassName + " generateHeaderExceptions..");

		if (ir.getOrders().size() == 0) {
			// if invoice is not against orders
			return exceptions;
		}

		for (int i = 0; i < exceptions.size(); i++) {
			ReconciliationException re = (ReconciliationException) exceptions
					.get(i);
			if (re.getType().getAbsoluteRank() < PO_AMOUNT_ABSOLUTE_RANK) {
				// do not manually generate PO_AMOUNT exception, if there is
				// already a higher level header exception
				return exceptions;
			}
		}

		/*
		 * VC modified to prevent POAmount Varinace from appearing if
		 * (ir.getConsolidated()) { //if summary invoice return exceptions; }
		 *
		 * //Check for PO Amount variance exceptions =
		 * generateRelativeMoneyException(ir, exceptions, PO_AMOUNT); return
		 * exceptions;
		 */

		return exceptions;
	}

	/**
	 * Generate any custom Line Exceptions
	 */
	private List generateLineExceptions(InvoiceReconciliationLineItem irli,
			List typesToValidate, List exceptions) {
		Log.customer.debug(ClassName + " generateLineExceptions..");

		if (irli.getOrder() == null) {
			// if invoice line is not against an order
			return exceptions;
		}

		// Adding check for negative invoice lines - credits, if Negative, just
		// skip
		if (irli.getAmount().isNegative()) {
			return exceptions;
		}

		for (int i = 0; i < exceptions.size(); i++) {
			ReconciliationException re = (ReconciliationException) exceptions
					.get(i);
			if (re.getType().getAbsoluteRank() < PO_PRICE_ABSOLUTE_RANK) {
				// do not manually generate lineExcTypes[], if there is already
				// a higher level line exception
				return exceptions;
			}
		}

		// Validating matched Line Items, catalog, non-catalog, punchout, etc.
		if (irli.getLineType().getCategory() == ProcureLineType.LineItemCategory) {
			int j = exceptions.size();
			for (int i = 0; i < lineExcTypes.length; i++) {
				if (lineExcTypes[i].equals(PO_CATALOG_PRICE)) {
					if (!irli.getLineType().isCatalogItemType()) {
						continue;
					}
				}
				if (lineExcTypes[i].equals(PO_PRICE)) {
					if (irli.getLineType().isCatalogItemType()) {
						continue;
					}
				}
				exceptions = generateRelativeMoneyException(irli, exceptions,
						lineExcTypes[i]);
				if (exceptions.size() > j) {
					break;
				}
			}
		}

		return exceptions;
	}

	/*
	 * Generate Passed in Exception, if ExceptionType is a Amount exception
	 * beyond tolerance
	 */
	private List generateRelativeMoneyException(BaseObject parent,
			List exceptions, String excTypeName) {
		InvoiceExceptionType excType = super.getInvoiceExceptionType(
				excTypeName, parent.getPartition());
		Money validate = (Money) parent.getDottedFieldValue(excType
				.getFieldPathToValidate());
		Money validateAgainst = (Money) parent.getDottedFieldValue(excType
				.getFieldPathToValidateAgainst());
		BigDecimal percentageTolerance = excType.getPercentageTolerance();
		BigDecimal amountTolerance = excType.getAbsoluteTolerance();
		if (!compareValuesTolerance(validate, validateAgainst, amountTolerance,
				percentageTolerance)) {
			exceptions.add(InvoiceException.createFromTypeAndParent(excType,
					parent));
		}
		return exceptions;
	}

	/**
	 * Compare two money objects to ensure that they are either within global
	 * tolerance. Difference cannot be greater than 25 and Percentage must be
	 * within 2.5% Assume Money is same currency Percentage (validate -
	 * validateAgainst)*100 / validateAgainst If within tolerance, return true
	 * Negative lines will always return true
	 */
	private static boolean compareValuesTolerance(Money validate,
			Money validateAgainst, BigDecimal amountTolerance,
			BigDecimal percentageTolerance) {

		if (validate == null || validateAgainst == null
				|| amountTolerance == null || percentageTolerance == null) {
			Log.customer
					.debug(ClassName
							+ "null parametrs passed to compareValuesTolerance method...");
			return false;
		}

		if (validate.isNegative()) {
			return true;
		}

		BigDecimal amount1 = validate.getAmount();
		BigDecimal amount2 = validateAgainst.getAmount();

		if (amount1 == null || amount2 == null || amount2.signum() == 0) {
			return false;
		}

		percentageTolerance = (percentageTolerance.subtract(OneBigDecimal))
				.multiply(OneHundredBigDecimal);

		BigDecimal diff = amount1.subtract(amount2);
		BigDecimal diffx100 = diff.multiply(OneHundredBigDecimal);
		BigDecimal percent = diffx100.divide(amount2, BigDecimal.ROUND_HALF_UP);

		Log.customer.debug(ClassName
				+ " compareValuesTolerance.. validate is: " + validate
				+ " validateAgainst is: " + validateAgainst);
		Log.customer.debug(ClassName
				+ " compareValuesTolerance.. amountTolerance is: "
				+ amountTolerance + " percentageTolerance is: "
				+ percentageTolerance);

		Log.customer.debug(ClassName + " compareValuesTolerance.. Percent is: "
				+ percent + "Diff is: " + diff);

		if (percentageTolerance.compareTo(percent) < 0
				|| amountTolerance.compareTo(diff) < 0) {
			return false;
		}

		return true;
	}

	private void defaultAccountingOnIRLineLevelAdditionalCharges(
			Approvable approvable) {

		InvoiceReconciliation ir = (InvoiceReconciliation) approvable;
		Invoice inv = ir.getInvoice();

		if (!ir.getInvoice().isStandardInvoice()) {
			return;
		}

		if (inv.isStandardInvoice()) {

			BaseVector v = inv.getLineItems();
			if (v.size() > 0) {
				InvoiceLineItem ili = (InvoiceLineItem) v.get(0);

				PurchaseOrder po = ili.getOrder();
				if (po != null) {
					ClusterRoot vatClass = (ClusterRoot) po
							.getDottedFieldValue("SupplierLocation.VATClass");
					Log.customer.debug("VAT Class value: "
							+ vatClass.getUniqueName());

					BaseVector irLineItems = ir.getLineItems();

					InvoiceReconciliationLineItem irLineItem = null;

					for (int i = 0; i < irLineItems.size(); i++) {
						irLineItem = (InvoiceReconciliationLineItem) irLineItems
								.get(i);
						if (irLineItem.getLineType().getCategory() == 4) {
							ClusterRoot zeroVAT = Base.getSession()
									.objectFromName("0", "cat.core.VATClass",
											ili.getPartition());
							Log.customer.debug("Setting zeroVAT = %s", zeroVAT);
							irLineItem.setDottedFieldValue("VATClass", zeroVAT);
							InvoiceLineItem invLineItem = irLineItem
									.getInvoiceLineItem();
							invLineItem
									.setDottedFieldValue("VATClass", zeroVAT);
						} else if (irLineItem.getLineType().getCategory() == 2) {
							irLineItem.setDottedFieldValue("VATClass", null);
							InvoiceLineItem invLineItem = irLineItem
									.getInvoiceLineItem();
							invLineItem.setDottedFieldValue("VATClass", null);
						} else if ((irLineItem.getDottedFieldValue("VATClass") != null)
								&& (irLineItem.getDottedFieldValue("VATClass") != vatClass)) {
							// is VATClass is already present and is not equal
							// to the supplier's default vatclass.. do nothing
						} else {
							irLineItem
									.setDottedFieldValue("VATClass", vatClass);
							InvoiceLineItem invLineItem = irLineItem
									.getInvoiceLineItem();
							invLineItem.setDottedFieldValue("VATClass",
									vatClass);
						}

						if (irLineItem.getDottedFieldValue("TaxAmount") == null) {
							InvoiceLineItem invLineItem = irLineItem
									.getInvoiceLineItem();
							if (invLineItem.getFieldValue("TaxAmount") != null) {
								Money taxAmount = (Money) invLineItem
										.getFieldValue("TaxAmount");
								BigDecimal taxAmountVal = (BigDecimal) taxAmount
										.getAmount();
								Log.customer.debug(
										"TaxAmount value as on Invoice line: ",
										taxAmountVal);
								irLineItem.setDottedFieldValue("TaxAmount",
										taxAmount);
							}

						}

					}

				} else {
					Contract ma = ili.getMasterAgreement();
					if (ma != null) {
						ClusterRoot vatClass = (ClusterRoot) ma
								.getDottedFieldValue("SupplierLocation.VATClass");
						Log.customer.debug("VAT Class value: "
								+ vatClass.getUniqueName());

						BaseVector irLineItems = ir.getLineItems();

						InvoiceReconciliationLineItem irLineItem = null;

						for (int i = 0; i < irLineItems.size(); i++) {
							irLineItem = (InvoiceReconciliationLineItem) irLineItems
									.get(i);
							if (irLineItem.getLineType().getCategory() == 4) {
								ClusterRoot zeroVAT = Base.getSession()
										.objectFromName("0",
												"cat.core.VATClass",
												ili.getPartition());
								Log.customer.debug("Setting zeroVAT = %s",
										zeroVAT);
								irLineItem.setDottedFieldValue("VATClass",
										zeroVAT);
								InvoiceLineItem invLineItem = irLineItem
										.getInvoiceLineItem();
								invLineItem.setDottedFieldValue("VATClass",
										zeroVAT);
							} else if (irLineItem.getLineType().getCategory() == 2) {
								irLineItem
										.setDottedFieldValue("VATClass", null);
								InvoiceLineItem invLineItem = irLineItem
										.getInvoiceLineItem();
								invLineItem.setDottedFieldValue("VATClass",
										null);
							} else if ((irLineItem
									.getDottedFieldValue("VATClass") != null)
									&& (irLineItem
											.getDottedFieldValue("VATClass") != vatClass)) {
								// is VATClass is already present and is not
								// equal to the supplier's default vatclass.. do
								// nothing
							} else {
								irLineItem.setDottedFieldValue("VATClass",
										vatClass);
								InvoiceLineItem invLineItem = irLineItem
										.getInvoiceLineItem();
								invLineItem.setDottedFieldValue("VATClass",
										vatClass);
							}

							if (irLineItem.getDottedFieldValue("TaxAmount") == null) {
								InvoiceLineItem invLineItem = irLineItem
										.getInvoiceLineItem();
								if (invLineItem.getFieldValue("TaxAmount") != null) {
									Money taxAmount = (Money) invLineItem
											.getFieldValue("TaxAmount");
									BigDecimal taxAmountVal = (BigDecimal) taxAmount
											.getAmount();
									Log.customer
											.debug(
													"TaxAmount value as on Invoice line: ",
													taxAmountVal);
									irLineItem.setDottedFieldValue("TaxAmount",
											taxAmount);
								}

							}
						}
					}
				}
			} else {
				Log.customer.debug("No line items on the invoice "
						+ inv.getUniqueName());
			}

		}
		CatMFGDefaultAccountingOnAdditionalCharges.defaultAccountingOnLines(ir);

		BaseVector irLineItems1 = ir.getLineItems();

		InvoiceReconciliationLineItem irLineItem1 = null;

		for (int i = 0; i < irLineItems1.size(); i++) {
			irLineItem1 = (InvoiceReconciliationLineItem) irLineItems1.get(i);
			if (irLineItem1.getLineType().getCategory() != ProcureLineType.LineItemCategory) {
				InvoiceLineItem invLineItem1 = irLineItem1.getInvoiceLineItem();
				invLineItem1.setAccountings(irLineItem1.getAccountings());
			}
		}

		ir.save();

	}

	// Start Of A4 By Kingshuk
	private void checkIfIRToBeRejected(Approvable approvable) {
		Assert
				.that(approvable instanceof InvoiceReconciliation,
						"%s.validateHeader: approvable must be an IR",
						"config.java.invoicing.vcsv2.CatMFGInvoiceReconciliationEngine");
		InvoiceReconciliation ir = (InvoiceReconciliation) approvable;
		BigDecimal percentage;
		int VATValue = -1;
		int vatClassValue = -1;
		List listValue = null;
		float percentageValue = 0;

		float vatratechange = 0;
		int invLoadingCat = (ir.getInvoice()).getLoadedFrom();
		BaseVector taxDetails = ir.getTaxDetails();
		if (invLoadingCat == 1 || invLoadingCat == 2) {
			Log.customer.debug(ClassName + " Tax Details Size: "
					+ taxDetails.size());
			if (taxDetails.size() > 0) {
				if (((TaxDetail) taxDetails.get(0)).getPercent() != null) {
					String supplierLocUniqueName = (String) ir
							.getDottedFieldValue("SupplierLocation.UniqueName");
					Log.customer.debug(
							"%s value for IR supplierLocUniqueName  is %s ",
							ClassName, supplierLocUniqueName);
					listValue = config.java.common.CatCommonUtil
							.makeValueListFromFile(supplierLocUniqueName,
									"/msc/arb821/Server/config/variants/vcsv2/data/CATMFGSupplierVATReference.csv");

					for (Iterator iterator = listValue.iterator(); iterator
							.hasNext();) {
						String fileVATClassValue = (String) iterator.next();
						VATValue = Integer.parseInt(fileVATClassValue.trim());
					}
					percentage = (java.math.BigDecimal) ((TaxDetail) taxDetails
							.get(0)).getPercent();
					if (percentage != null) {
						// Float f_vat = new Float(VAT_RATE_CHANGED);
						// vatratechange = f_vat.floatValue();
						String fvat = BigDecimalFormatter
								.getStringValue(percentage);
						Log.customer.debug("Value of fvat" + fvat);
						Log.customer
								.debug("value for IR supplierLocUniqueName  is "
										+ fvat);
						percentageValue = percentage.floatValue();
						Log.customer
								.debug("value for IR supplierLocUniqueName  is "
										+ percentageValue);
						if ((percentageValue == 0 && percentage != null)) {
							vatClassValue = 0;
						}
						// Code changed for UK VAT ( from 17.5 to 15 )
						// Code changed for UK VAT ( from 15 to 17.5)
						else if (fvat.equals(VAT_RATE_CHANGED)) {
							vatClassValue = 1;
							Log.customer
									.debug("the VAt rate is set as percentage ");
						} else if (percentageValue == 5) {
							vatClassValue = 8;
						} else {
							Log.customer
									.debug(
											"%s TaxDetail Percent %s IS NOT matching either of 0 Or 15 Or 5 percent",
											ClassName, percentage);
							Log.customer.debug("%s Hence Rejecting the IR",
									ClassName);
							ir.reject();
						}
					} else {
						Log.customer.debug(
								"%s TaxDetail Percent IS NULL Not Rejecting",
								ClassName);
					}

					if (vatClassValue != VATValue) {
						Log.customer
								.debug(
										"%s TaxDetail Percent %s IS NOT Matching with Supplier VATClass %s",
										ClassName, vatClassValue, VATValue);
						Log.customer.debug("%s Hence Rejecting the IR",
								ClassName);
						ir.reject();
					}
				}
			}
		}
	}
	// End Of A4 By Kingshuk

}
