/*
 * Created on Oct 8, 2010
 *
 * Created by PGS Kannan
 * Useage :
 *
 *  Change History
 *  Change By			Change Date		Description
 *  IBM Abhishek Kumar	7/17/2013		Mach1 R5.5 (FRD1.5/TD1.3) Logic added to consolidate multiple tax lines into one tax line
 * ==================================================================================================================================
 */
package config.java.schedule.vcsv1;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.TimeZone;

import ariba.base.core.Base;
import ariba.base.core.BaseVector;
import ariba.base.core.ClusterRoot;
import ariba.base.core.Partition;
import ariba.base.core.aql.AQLOptions;
import ariba.base.core.aql.AQLQuery;
import ariba.base.core.aql.AQLResultCollection;
import ariba.basic.core.Money;
import ariba.common.core.SplitAccounting;
import ariba.common.core.SplitAccountingCollection;
import ariba.invoicing.core.InvoiceReconciliation;
import ariba.invoicing.core.InvoiceReconciliationLineItem;
import ariba.util.core.Constants;
import ariba.util.core.Date;
import ariba.util.core.FastStringBuffer;
import ariba.util.core.IOUtil;
import ariba.util.formatter.DateFormatter;
import ariba.util.log.Log;
import ariba.util.scheduler.ScheduledTask;
import ariba.util.scheduler.ScheduledTaskException;
import config.java.common.CatEmailNotificationUtil;
import config.java.schedule.util.CATFaltFileUtil;
import ariba.common.core.Address ;
import ariba.util.formatter.BigDecimalFormatter;

public class CATCAPSInvoiceFlatFilePush extends ScheduledTask {

	private String classname = "CATCAPSInvoiceFlatFilePush";
	private String fileExtDateTime = "";
	private String flatFilePath = "";
	private String controlFlatFilePath = "";
	//change made by soumya begings
	private String archiveFileDataPath;
	private String archiveFileCtrlPath;
	//change made by soumya ends
	private String triggerFile = "";

	private AQLOptions options;
	private Partition partition = Base.getService().getPartition("pcsv1");

	private AQLQuery aqlIRQuery,aqlctrlQuery;
	private AQLResultCollection irResultSet,ctrlResultSet;
	private int totalNumberOfIrsCAPS;
	private String startTime, endTime;
	private FastStringBuffer message = null;
	private String mailSubject = null;
	private int totalNumberOfIrs;
	private BaseVector IrLineItem=null;
	private int lineCount,IrLineItemvector,IrnumberInCollection,irLinePoLineNumber,irSplitaccSize;
	private SplitAccountingCollection irAccounting = null;
	private BaseVector irSplitAccounting = null;
	private String irAccFac= null;
	private String irOrdername = null;
	private String irDivisionString = null;
	private String irSectionString = null;
	private String irExpenseAccountString = null;
	private String irMiscString  = null;
	private String ilRecvFacilityCode = null;
	private SplitAccounting irSplitAccounting2 = null;
	private String irAccountingFacility = null;
	private String irAccountingRecFacility = null;

	private Integer iCAPSLineNo;
	private String sCAPSLineNo;
	private SplitAccounting splitAcc;
	private boolean IsForeign;
	private BigDecimal totamt,InvoiceSplitDiscountDollarAmount,TotalInvoiceAmountMinusDiscount;
	// Start: Mach1 R5.5 (FRD1.5/TD1.3)
	private String iRFmtSupplierLocation,iRFmtBlockStampDate,iRFmtTotalCost,iRFmtDiscountTotalCost,iRFmtiRTotalCostMinusDiscount,iRFmtCurrency,iRFmtFgnTotalCost,IRFmtUSExchangeRate,IRFmtCurrencyTimeUpdated,iRFmtSupplierInvoiceDate,iRFmtInvoiceNumber,iRFmtBtchNO,iRFmtEntMethInd,iRFmtinvcLnTyp,iRFmtApInvcLnNo,iRFmtRcvgFacCd,irFmtShpDt,iRFmtSuppShpRef1,iRFmtSuppShpRef2,iRFmtPONumber,iRFmtCatIdNo20,iRFmtCatIdClsCD,irFmtIlQuantity,iRFmtPrcxUm1,iRFmtApproxAmountInBaseCurrency,iRFmtApproxAmountInBaseCurrency2,irFmtLnTermsDiscountPercent,iRLnFmtDiscountApproxAmountInBaseCurrency,iRLnFmtSplitAccountingsAmountAmount,iRLnFmtSplitAccountingsAmountAmount2,irFmtERPTaxCode,irFmtIRTaxRate,iRLnFmtSplitAccountingsAccountingFacility,iRLnFmtSplitAccountingsDepartment,iRLnFmtSplitAccountingsDivision,iRLnFmtSplitAccountingsSection,iRLnFmtSplitAccountingsExpenseAccount,iRLnFmtSplitAccountingsOrder,iRLnFmtSplitAccountingsMisc,iRLnFmtInvQty,iRLnFmtInvUm,iRLnFmtCnvrtInvQty,irLnFmtTaxCodeUniqueName,irLnFmtTaxRate,irLnFmtDesc,iRFmtControlDate,fmtFiller,iRLntotalStrFmtSplitAccountingsAmountAmount,iRLntotalStrFmtSplitAccountingsAmountAmount2,iRtotalFmtApproxAmountInBaseCurrency,iRtotalFmtApproxAmountInBaseCurrency2;
	// End: Mach1 R5.5 (FRD1.5/TD1.3)
	private String controlid;
	private Date datetimezone;
	private java.math.BigDecimal bdTotCost;
	private int iSpAcct ;


	private PrintWriter outPW_FlatFile = null;
	private PrintWriter outPW_CTRLFlatFile = null;
	boolean isPushed = false;
	InvoiceReconciliation invrecon = null;

	/* (non-Javadoc)
	 * @see ariba.util.scheduler.ScheduledTask#run()
	 */
	public void run() throws ScheduledTaskException {
		int pushedCount = 0;
		int resultCount = 0;

		try {
			datetimezone = new Date();
			startTime =	ariba.util.formatter.DateFormatter.getStringValue(	new ariba.util.core.Date(),	"EEE MMM d hh:mm:ss a z yyyy",TimeZone.getTimeZone("CST"));
			message = new FastStringBuffer();
			mailSubject ="CATCAPSInvoiceFlatFile Task Completion Status - Completed Successfully";
			Date date = new Date();
			fileExtDateTime = CATFaltFileUtil.getFileExtDateTime(date);
			flatFilePath = "/msc/arb9r1/downstream/catdata/INV/MSC_CAPS_INVOICE_PUSH."+ fileExtDateTime + ".txt";
			controlFlatFilePath = "/msc/arb9r1/downstream/catdata/INV/MSC_CAPS_INVOICE_CTRL."+ fileExtDateTime + ".txt";
			triggerFile = "/msc/arb9r1/downstream/catdata/INV/MSC_CAPS_INVOICE_PUSH."+ fileExtDateTime + ".dstrigger";
			//Change made by soumya begins
			archiveFileDataPath = "/msc/arb9r1/downstream/catdata/INV/archive/MSC_CAPS_INVOICE_PUSH_ARCHIVE." + fileExtDateTime + ".txt";
			archiveFileCtrlPath = "/msc/arb9r1/downstream/catdata/INV/archive/MSC_CAPS_INVOICE_CTRL_ARCHIVE." + fileExtDateTime + ".txt";
			//Change made by soumya ends

			Log.customer.debug("flatFilePath " + flatFilePath);
			Log.customer.debug("controlFlatFilePath " + controlFlatFilePath);
			Log.customer.debug("triggerFile " + triggerFile);
			//Change made by soumya begins
			Log.customer.debug("CATCAPSInvoiceFlatFilePush:archiveFileDataPath " + archiveFileDataPath);
			Log.customer.debug("CATCAPSInvoiceFlatFilePush:archiveFileCtrlPath " + archiveFileCtrlPath);
			//Change made by soumya ends

			File capsIRFlatFile = new File(flatFilePath);
			File capsIRCTRLFlatFile = new File(controlFlatFilePath);


			options = new AQLOptions(partition);

			if (!capsIRFlatFile.exists()) {
				Log.customer.debug("File not exist creating file ..");
				capsIRFlatFile.createNewFile();
			}

			if (!capsIRCTRLFlatFile.exists()) {
				Log.customer.debug("CTRL File not exist creating file ..");
				capsIRCTRLFlatFile.createNewFile();
			}



			outPW_FlatFile =new PrintWriter(IOUtil.bufferedOutputStream(capsIRFlatFile),true);
			Log.customer.debug("outPW_FlatFile " + outPW_FlatFile);

			outPW_CTRLFlatFile =new PrintWriter(IOUtil.bufferedOutputStream(capsIRCTRLFlatFile),true);
			Log.customer.debug("outPW_CTRLFlatFile " + outPW_CTRLFlatFile);



			String iRQuery = new String( "select from ariba.invoicing.core.InvoiceReconciliation  where "
						                + "ActionFlag = 'InProcess'");
			Log.customer.debug("iRQuery ==> " + iRQuery);
			aqlIRQuery = AQLQuery.parseQuery(iRQuery);
			irResultSet = Base.getService().executeQuery(aqlIRQuery, options);

			if (irResultSet.getErrors() != null)
				Log.customer.debug("ERROR GETTING RESULTS in irResultSetDX");

			totalNumberOfIrs = irResultSet.getSize();

			resultCount = totalNumberOfIrs;
			Log.customer.debug("totalNumberOfIrs ==> " + totalNumberOfIrs);
			int commitCount = 0;

			while(irResultSet.next()){

					invrecon = (InvoiceReconciliation)(irResultSet.getBaseId("InvoiceReconciliation").get());
					isPushed = false;

					if(invrecon != null){



						int lineCount = invrecon.getLineItemsCount();
						BaseVector IrLineItemVector = (BaseVector)invrecon.getLineItems();

						Log.customer.debug("%s::Line Item count for IR:%s ",classname,lineCount);
						if (lineCount > 0){

						iSpAcct = 0;
						// Start: Mach1 R5.5 (FRD1.5/TD1.3)
						BigDecimal iRLntotalFmtSplitAccountingsAmountAmount = new BigDecimal(0.00);
						BigDecimal iRLntotalFmtSplitAccountingsAmountAmount2 = new BigDecimal(0.00);
						BigDecimal irtotalIlApproxAmountInBaseCurrency = new BigDecimal(0.00);
						BigDecimal irtotalIlApproxAmountInBaseCurrency2 = new BigDecimal(0.00);
						// End: Mach1 R5.5 (FRD1.5/TD1.3)
						for (int i =0; i<lineCount;i++){
							Log.customer.debug("%s::inside for (int i =0; i<IrLineItem.size();i++) i value %s ",classname,i);
							InvoiceReconciliationLineItem IrLineItem2 = (InvoiceReconciliationLineItem)IrLineItemVector.get(i);
							Log.customer.debug("%s::... IrLineItem2 %s ",classname,IrLineItem2);

							irAccounting = IrLineItem2.getAccountings();
							Log.customer.debug("%s::... irAccounting %s ",classname,irAccounting);

						if (irAccounting!=null){

							Log.customer.debug("%s::inside if (irAccounting!=null) %s ",classname,irAccounting);

							irSplitAccounting = (BaseVector)irAccounting.getSplitAccountings();
							irSplitaccSize = irSplitAccounting.size();

							Log.customer.debug("%s::Split acc size:%s",classname,irSplitaccSize);

							if (irSplitaccSize > 0){
								// Start: Mach1 R5.5 (FRD1.5/TD1.3)
								//iSpAcct+= irSplitaccSize;
								// End: Mach1 R5.5 (FRD1.5/TD1.3)
							for(Iterator s= irAccounting.getSplitAccountingsIterator(); s.hasNext();) {
								SplitAccounting splitAcc = (SplitAccounting) s.next();


							if (splitAcc != null) {


								controlid = getDateTime(datetimezone) + controlid;
								Log.customer.debug("ControlIdentifier IS..." + controlid);
								invrecon.setFieldValue("ControlIdentifier",controlid);
								invrecon.setFieldValue( "ControlDate", datetimezone );
								if (invrecon.getDottedFieldValue("TotalCost.ApproxAmountInBaseCurrency") != null)
									bdTotCost = (java.math.BigDecimal)invrecon.getDottedFieldValue("TotalCost.ApproxAmountInBaseCurrency");


								Log.customer.debug ("%s::Inside splitAcc != null",classname);




						//   AP-BUY-SUPP-ID  X(10)
						String iRSupplierLocation = invrecon.getDottedFieldValue("Supplier.UniqueName").toString();
						Log.customer.debug("iRSupplierLocation ==> " +iRSupplierLocation);
						iRFmtSupplierLocation = CATFaltFileUtil.getFormattedTxt(iRSupplierLocation,10);
						Log.customer.debug("iRFmtSupplierLocation ==> " +iRFmtSupplierLocation);

						//   BLK-STMP-DT X(10)
						Date iRBlockStampDate = (Date)invrecon.getFieldValue("BlockStampDate");
						Log.customer.debug("iRBlockStampDate ==> " +iRBlockStampDate);
						iRFmtBlockStampDate = CATFaltFileUtil.getFormattedDate(iRBlockStampDate);
						Log.customer.debug("iRFmtBlockStampDate ==> " +iRFmtBlockStampDate);

						//   TOT-INVC-AMTX X(17)
						// Change by VJS
						//double iRTotalCost  = invrecon.getTotalCost().getAmountAsDouble();
						//Log.customer.debug("iRTotalCost ==> " +iRTotalCost);
						double iRTotalCostDbVal = 0.0;
						BigDecimal iRTotCostBigVal = (BigDecimal)invrecon.getDottedFieldValue("TotalCost.ApproxAmountInBaseCurrency");
						iRTotalCostDbVal = iRTotCostBigVal.doubleValue();
						iRFmtTotalCost = CATFaltFileUtil.getFormattedNumber(iRTotalCostDbVal);
						Log.customer.debug("iRFmtTotalCost ==> " +iRFmtTotalCost);

						//   TOT-INVC-DISC-AMTX      	X(17)

						/*
						Money iRDiscountTotalCostMoney = (Money)invrecon.getFieldValue("TotalInvoiceDiscountDollarAmount");

						double iRDiscountTotalCost  = iRDiscountTotalCostMoney.getAmountAsDouble();
						Log.customer.debug("iRDiscountTotalCost ==> " +iRDiscountTotalCost);
						iRFmtDiscountTotalCost = CATFaltFileUtil.getFormattedNumber(iRDiscountTotalCost);
						Log.customer.debug("iRFmtDiscountTotalCost ==> " +iRFmtDiscountTotalCost);
                        */
						// Change by VJS

						double iRDiscountTotalCost = 0.0;
						BigDecimal DiscountBigVal = (BigDecimal)invrecon.getDottedFieldValue("TotalInvoiceDiscountDollarAmount.ApproxAmountInBaseCurrency");
						iRDiscountTotalCost = DiscountBigVal.doubleValue();
						iRFmtDiscountTotalCost = CATFaltFileUtil.getFormattedNumber(iRDiscountTotalCost);
						Log.customer.debug("iRFmtDiscountTotalCost ==> " +iRFmtDiscountTotalCost);


						//   TOT-INVC-PMT-AMTX       	X(17)

						/*
						Money iRTotalCostMinusDiscountMoney = (Money)invrecon.getFieldValue("TotalInvoiceAmountMinusDiscount");
						double iRTotalCostMinusDiscount  = iRTotalCostMinusDiscountMoney.getAmountAsDouble();
						Log.customer.debug("iRTotalCostMinusDiscount ==> " +iRTotalCostMinusDiscount);
						iRFmtiRTotalCostMinusDiscount = CATFaltFileUtil.getFormattedNumber(iRTotalCostMinusDiscount);
						Log.customer.debug("iRFmtiRTotalCostMinusDiscount ==> " +iRFmtiRTotalCostMinusDiscount);
						*/

						// Change by VJS

						double iRTotalCostMinusDiscount = 0.0;
						BigDecimal InvMinDiscBigV = (BigDecimal)invrecon.getDottedFieldValue("TotalInvoiceAmountMinusDiscount.ApproxAmountInBaseCurrency");
						iRTotalCostMinusDiscount = InvMinDiscBigV.doubleValue();
						iRFmtiRTotalCostMinusDiscount = CATFaltFileUtil.getFormattedNumber(iRTotalCostMinusDiscount);
						Log.customer.debug("iRFmtiRTotalCostMinusDiscount ==> " +iRFmtiRTotalCostMinusDiscount);


						//   CCY-CD          		X(3)
						String iRCurrency = invrecon.getDottedFieldValue("ReportingCurrency.UniqueName").toString();
						Log.customer.debug("iRCurrency ==> " +iRCurrency);
						iRFmtCurrency = CATFaltFileUtil.getFormattedTxt(iRCurrency,3);
						Log.customer.debug("iRFmtiRCurrency ==> " +iRFmtCurrency);

						//   FGN-INVC-AMTX          	X(17)  If Currency.UniqueName is = USD send Zero's +0.00)
						// Added else condition by VJS
						String iRFmtFgnTotalCost = "+0.00";
						double iRFgnTotalCost= invrecon.getTotalCost().getAmountAsDouble();
						Log.customer.debug("iRFgnTotalCost ==> " +iRFgnTotalCost);
						Log.customer.debug("iRCurrency ==> " +iRCurrency);

						if ( !iRCurrency.equals("USD")) {
							Log.customer.debug("inside ..iRCurrency ==> " +iRCurrency);
							//BigDecimal iRFgnTotalCostBigDec = (BigDecimal)invrecon.getDottedFieldValue("TotalCost.ApproxAmountInBaseCurrency"); - Change by VJS
						    BigDecimal iRFgnTotalCostBigDec = (BigDecimal)invrecon.getDottedFieldValue("TotalCost.Amount");
							iRFgnTotalCost = iRFgnTotalCostBigDec.doubleValue();
							iRFmtFgnTotalCost = CATFaltFileUtil.getFormattedNumber(iRFgnTotalCost);

						}
						else
						{
							iRFmtFgnTotalCost = "+0.00            ";
						}


						Log.customer.debug("iRFmtFgnTotalCost ==> " +iRFmtFgnTotalCost);



						//   PEG-RATE          	X(17)
						/*
						String IRFmtUSExchangeRate = "0000001.00000000 ";
						if ( !iRCurrency.equals("USD")) {
							BigDecimal IRUSExchangeRateStr = (BigDecimal)invrecon.getFieldValue("USExchangeRate");
							double IRUSExchangeRateDbl = IRUSExchangeRateStr.doubleValue();
							String IRUSExchangeRateDblStr = ""+IRUSExchangeRateDbl;
							IRFmtUSExchangeRate = CATFaltFileUtil.getFormattedTxt(IRUSExchangeRateDblStr,17);

							Log.customer.debug("IRFmtUSExchangeRate for non USD Currency ==> " +IRFmtUSExchangeRate);

						}
						else {
							Log.customer.debug("IRFmtUSExchangeRate for USD Currency ==> " +IRFmtUSExchangeRate);
						}
						*/

						String IRFmtUSExchangeRate = "0000001.00000000 ";
						if ( !iRCurrency.equals("USD")) {
							BigDecimal IRUSExchangeRateStr = (BigDecimal)invrecon.getFieldValue("USExchangeRate");
							double IRUSExchangeRateDbl = IRUSExchangeRateStr.doubleValue();
							IRFmtUSExchangeRate = CATFaltFileUtil.getFormattedNumber2(IRUSExchangeRateDbl,"0000000.00000000");

							Log.customer.debug("IRFmtUSExchangeRate for non USD Currency ==> " +IRFmtUSExchangeRate);

						}
						else {
							IRFmtUSExchangeRate = "0000001.00000000 ";
							Log.customer.debug("IRFmtUSExchangeRate for USD Currency ==> " +IRFmtUSExchangeRate);
						}


						//   PEG-DT			X(10)  //

						String IRCurrencyTimeUpdated = "0001-01-01";
						if ( !iRCurrency.equals("USD")) {
							//Date IRFrgCurrencyTimeUpdated = (Date)invrecon.getDottedFieldValue("ReportingCurrency.TimeUpdated"); -- Commented out and added below line by VJS
							Date IRFrgCurrencyTimeUpdated = (Date)invrecon.getDottedFieldValue("TotalCost.ConversionDate");
							Log.customer.debug("IRCurrencyTimeUpdated ==> " +IRCurrencyTimeUpdated);
							IRFmtCurrencyTimeUpdated = CATFaltFileUtil.getFormattedDate(IRFrgCurrencyTimeUpdated);

						}
						else
							IRFmtCurrencyTimeUpdated = IRCurrencyTimeUpdated;

						Log.customer.debug("IRFmtCurrencyTimeUpdated ==> " +IRFmtCurrencyTimeUpdated);

						//   SUPP-INVC-DT          	X(10)
						Date iRSupplierInvoiceDate  =null;
						 if ( invrecon.getFieldValue("InvoiceDate") != null) {
						          iRSupplierInvoiceDate =  (Date )invrecon.getFieldValue("InvoiceDate");
						 }
						 else {
							 iRSupplierInvoiceDate = new Date();
							 Log.customer.debug("iRSupplierInvoiceDate getting null date need to investigate  ==> " +iRSupplierInvoiceDate);

						 }
						Log.customer.debug("iRSupplierInvoiceDate ==> " +iRSupplierInvoiceDate);
						iRFmtSupplierInvoiceDate = CATFaltFileUtil.getFormattedDate(iRSupplierInvoiceDate);
						Log.customer.debug("iRFmtSupplierInvoiceDate ==> " +iRFmtSupplierInvoiceDate);



						//   SUPP-INVC-NO-24        	X(24)
						String iRInvoiceNumber  ="";
						if ( invrecon.getFieldValue("InvoiceNumber") != null)
						iRInvoiceNumber =  invrecon.getFieldValue("InvoiceNumber").toString();
						Log.customer.debug("iRInvoiceNumber ==> " +iRInvoiceNumber);
						iRFmtInvoiceNumber = CATFaltFileUtil.getFormattedTxt(iRInvoiceNumber,24);
						Log.customer.debug("iRFmtInvoiceNumber ==> " +iRFmtInvoiceNumber);

						//   INVC-BTCH-NO		X(3)Always ' AI' space AI

						String iRFmtBtchNO  =" AI";
						Log.customer.debug("iRFmtBtchNO ==> " +iRFmtBtchNO);

						//   ENT-METH-IND		X(1) Always 'A'
						String iRFmtEntMethInd  ="A";
						Log.customer.debug("iRFmtEntMethInd ==> " +iRFmtEntMethInd);


						//   INVC-LN-TYP          	X(3) LineItems.CAPSChargeCode.UniqueName
						String invcLnTyp = null;
						if (IrLineItem2.getFieldValue("CapsChargeCode") != null){
							Log.customer.debug("CapsChargeCode is not null ==> " );
							invcLnTyp = IrLineItem2.getDottedFieldValue("CapsChargeCode.UniqueName").toString();
						}
						else{
							// added below line by VJS - Mainly to avoid null pointer exception
							invcLnTyp = "000";
							Log.customer.debug("CapsChargeCode is NULL  ==> " + invcLnTyp );
						}

						iRFmtinvcLnTyp = CATFaltFileUtil.getFormattedTxt(invcLnTyp, 3);
						Log.customer.debug("iRFmtinvcLnTyp ==> " +iRFmtinvcLnTyp);

						//   AP-INVC-LN-NO          	X(3) //


						//for(Iterator s= irAccounting.getSplitAccountingsIterator(); s.hasNext();) {
						//	SplitAccounting splitAcc = (SplitAccounting) s.next();
						  // if (splitAcc != null) {
							 Log.customer.debug("splitAcc IS..." + splitAcc);
							  String capsLineNumber = (String)splitAcc.getFieldValue("CapsLineNumber");
							  if(capsLineNumber == null)  {
						                 	Log.customer.debug("CapsLineNumber is null..calling GenerateCapsLineNumber");
						                    GenerateCapsLineNumber (invrecon);
							}
						//}

					  //}
						String apInvcLnNo = (String)splitAcc.getFieldValue("CapsLineNumber");
						Log.customer.debug("apInvcLnNo ==> " +apInvcLnNo);
						// iRFmtApInvcLnNo = CATFaltFileUtil.getFormattedTxt(apInvcLnNo, 3); -- Added below change by VJS
						iRFmtApInvcLnNo = CATFaltFileUtil.addLeadingZeros(apInvcLnNo,3);
						Log.customer.debug("iRFmtApInvcLnNo before npe ==> " +iRFmtApInvcLnNo);


						//   RCVG-FAC-CD          	X(2)  LineItems.ShipTo.ReceivingFacility
						//Left Justified When null send 'AA' This will need to be converted to all UPPER CASE.
						String iRRcvgFacCd = "";
						String iRFmtRcvgFacCd ="";
						if (IrLineItem2.getFieldValue("ShipTo") != null){

							Address shipTo = (Address)IrLineItem2.getFieldValue("ShipTo");
							Log.customer.debug(" ShipTo iRRcvgFacCd ==> " +shipTo);
							if (shipTo.getFieldValue("ReceivingFacility")!= null)
							     iRRcvgFacCd = shipTo.getFieldValue("ReceivingFacility").toString();
							Log.customer.debug("iRRcvgFacCd ==> " +iRRcvgFacCd);
						}
						else{
							iRRcvgFacCd = "AA";
							Log.customer.debug("iRRcvgFacCd ==> " +iRRcvgFacCd);
						}
						Log.customer.debug("before formatting iRRcvgFacCd ==> " +iRRcvgFacCd);
						iRFmtRcvgFacCd = CATFaltFileUtil.getFormattedTxt(iRRcvgFacCd.toUpperCase(), 2);
						Log.customer.debug("iRFmtRcvgFacCd ==> " +iRFmtRcvgFacCd);

						//   SHP-DT          		X(10) Always send '0001-01-01'
						String irFmtShpDt = "0001-01-01";
						Log.customer.debug("irFmtShpDt ==> " +irFmtShpDt);


						//   SUPP-SHP-REF-1         	X(24)
						String iRSuppShpRef1   = "";
						iRFmtSuppShpRef1 = null;
						if (splitAcc.getFieldValue("ContractFileNumber") != null)
						{
							iRSuppShpRef1  = splitAcc.getFieldValue("ContractFileNumber").toString();
							iRFmtSuppShpRef1 = CATFaltFileUtil.getFormattedTxt(iRSuppShpRef1, 24);
						}
						else
							iRFmtSuppShpRef1 = CATFaltFileUtil.getFormattedTxt(" ", 24);

						Log.customer.debug("iRFmtSuppShpRef1 ==> " +iRFmtSuppShpRef1);

						//   SUPP-SHP-REF-2          	X(24) UniqueName APPEND |pcsv1-| to the front and
						//then only send the last 18 characters. This will need to be converted to all UPPER CASE.

						String iRSuppShpRef2 = "";


						String invunique = (java.lang.String)invrecon.getFieldValue("UniqueName");
						String strunique = invunique ;
						Log.customer.debug("UniqueName Is..." + invunique);
						if (invunique.length() >= 18)
						{
							invunique = "pcsv1-" + invunique.substring(invunique.length() - 18);
						}
						else invunique = "pcsv1-" + invunique;

						Log.customer.debug("Last 24 Chars of the IR Is..." + invunique);

						controlid = new String (invunique);

						iRSuppShpRef2   = invunique;



						String iRFmtSuppShpRef2 = null;
						if (iRSuppShpRef2 != null)
						{
							iRFmtSuppShpRef2 = CATFaltFileUtil.getFormattedTxt(iRSuppShpRef2.toUpperCase(), 24);
						}
						Log.customer.debug("iRFmtSuppShpRef2 ==> " +iRFmtSuppShpRef2);

						//   PO-NO-15          	X(15)

						// Change by VJS to have 1. ending zeros for contracts 2. No version numbers for orders and contracts
						/*
						String iRPONumber   = invrecon.getFieldValue("PONumber").toString();
						iRFmtPONumber = null;
						if (iRPONumber != null)
						{
							iRFmtPONumber = CATFaltFileUtil.getFormattedTxt(iRPONumber, 15);
						}
						Log.customer.debug("iRFmtPONumber ==> " +iRFmtPONumber);
						*/

						String iRPONumber = "";
						String iRconNumber = "";
						iRFmtPONumber = null;
						if ( invrecon.getFieldValue("Order") != null )
						{
							iRPONumber   = invrecon.getDottedFieldValue("Order.InitialUniqueName").toString();
							iRFmtPONumber = CATFaltFileUtil.getFormattedTxt(iRPONumber, 15);
							Log.customer.debug("iRFmtPONumber ==> " +iRFmtPONumber);
						}
						else
						{
							iRconNumber   = invrecon.getDottedFieldValue("MasterAgreement.InitialUniqueName").toString();
							iRPONumber = CATFaltFileUtil.addEndingZeros(iRconNumber, 10);
							iRFmtPONumber = CATFaltFileUtil.getFormattedTxt(iRPONumber, 15);
							Log.customer.debug("iRFmtPONumber ==> " +iRFmtPONumber);
						}

						// Change by VJS to have supplier part number

						//   CAT-ID-NO-20          	X(10)  //Spaces
						/*String iRCatIdNo20   = " ";
						iRFmtCatIdNo20 = CATFaltFileUtil.getFormattedTxt(iRCatIdNo20, 10);
						Log.customer.debug("iRFmtCatIdNo20 ==> " +iRFmtCatIdNo20);
						*/

						String iRCatIdNo20   = "";
						iRCatIdNo20 = IrLineItem2.getDottedFieldValue("Description.SupplierPartNumber").toString();

						iRFmtCatIdNo20 = CATFaltFileUtil.getFormattedTxt(iRCatIdNo20, 10);
						Log.customer.debug("iRFmtCatIdNo20 ==> " +iRFmtCatIdNo20);


						//   CAT-ID-CLS-CD          	X(1)//Spaces
						String iRCatIdClsCD   = " ";
						iRFmtCatIdClsCD = CATFaltFileUtil.getFormattedTxt(iRCatIdClsCD, 1);
						Log.customer.debug("iRFmtCatIdClsCD ==> " +iRFmtCatIdClsCD);

						//   INVC-LN-BILL-QTY        	X(15)  LineItems. Accountings.SplitAccountings.Quantity

						BigDecimal irIlQuantity = (BigDecimal)splitAcc.getFieldValue("Quantity");
						double irIlQuantityDouble = irIlQuantity.doubleValue();
						String irFmtIlQuantity = null;
						if(irIlQuantity != null) {
							  irFmtIlQuantity = CATFaltFileUtil.getFormattedNumber(irIlQuantityDouble, "000000000.0000");
						 }
						Log.customer.debug("irFmtIlQuantity ==> " +irFmtIlQuantity);

						//   PRCX-UM-1          	X(4) Always send '+058'
						String iRPrcxUm1   = "+058";
						iRFmtPrcxUm1 = CATFaltFileUtil.getFormattedTxt(iRPrcxUm1, 4);
						Log.customer.debug("iRFmtPrcxUm1 ==> " +iRFmtPrcxUm1);

						//   LN-ITM-UNIT-PRCX        	X(17)  LineItems. Accountings.SplitAccountings.Amount.ApproxAmountInBaseCurrency
						BigDecimal irIlApproxAmountInBaseCurrency = null;
						iRFmtApproxAmountInBaseCurrency = "";
						if (splitAcc.getDottedFieldValue("Amount.ApproxAmountInBaseCurrency") != null){
							irIlApproxAmountInBaseCurrency = (BigDecimal)splitAcc.getDottedFieldValue("Amount.ApproxAmountInBaseCurrency");
							double irIlApproxAmountInBaseCurrencyDbl = irIlApproxAmountInBaseCurrency.doubleValue();
							iRFmtApproxAmountInBaseCurrency = CATFaltFileUtil.getFormattedNumber(irIlApproxAmountInBaseCurrencyDbl,"0000000000.00000");
						}
						Log.customer.debug("iRFmtApproxAmountInBaseCurrency ==> " +iRFmtApproxAmountInBaseCurrency);
						// Start: Mach1 R5.5 (FRD1.5/TD1.3)
						iRtotalFmtApproxAmountInBaseCurrency = "";
						if (iRFmtinvcLnTyp.equals("002") || iRFmtinvcLnTyp.equals("096") || iRFmtinvcLnTyp.equals("003"))
						{
							if (splitAcc.getDottedFieldValue("Amount.ApproxAmountInBaseCurrency") != null){
								irIlApproxAmountInBaseCurrency = (BigDecimal)splitAcc.getDottedFieldValue("Amount.ApproxAmountInBaseCurrency");
								irtotalIlApproxAmountInBaseCurrency = irtotalIlApproxAmountInBaseCurrency.add(irIlApproxAmountInBaseCurrency);
								double irtotalIlApproxAmountInBaseCurrencyDbl = irtotalIlApproxAmountInBaseCurrency.doubleValue();
								iRtotalFmtApproxAmountInBaseCurrency = CATFaltFileUtil.getFormattedNumber(irtotalIlApproxAmountInBaseCurrencyDbl,"0000000000.00000");
							}
						Log.customer.debug("iRtotalFmtApproxAmountInBaseCurrency ==> " +iRtotalFmtApproxAmountInBaseCurrency);
						}
						// End: Mach1 R5.5 (FRD1.5/TD1.3)
						//   LN-ITM-AMTX   X(17) LineItems.Accountings.SplitAccountings.Amount.ApproxAmountInBaseCurrency
						BigDecimal irIlApproxAmountInBaseCurrency2 = (BigDecimal)splitAcc.getDottedFieldValue("Amount.ApproxAmountInBaseCurrency");
						iRFmtApproxAmountInBaseCurrency2 = null;
						if (irIlApproxAmountInBaseCurrency2 != null){
							double irIlApproxAmountInBaseCurrency2Dbl = irIlApproxAmountInBaseCurrency2.doubleValue();
							iRFmtApproxAmountInBaseCurrency2 = CATFaltFileUtil.getFormattedNumber(irIlApproxAmountInBaseCurrency2Dbl);
						}
						Log.customer.debug("iRFmtApproxAmountInBaseCurrency2 ==> " +iRFmtApproxAmountInBaseCurrency2);
						// Start: Mach1 R5.5 (FRD1.5/TD1.3)
						iRtotalFmtApproxAmountInBaseCurrency2 = null;
						if (iRFmtinvcLnTyp.equals("002") || iRFmtinvcLnTyp.equals("096") || iRFmtinvcLnTyp.equals("003"))
						{
							if (irIlApproxAmountInBaseCurrency2 != null){
								irtotalIlApproxAmountInBaseCurrency2 = irtotalIlApproxAmountInBaseCurrency2.add(irIlApproxAmountInBaseCurrency2);
								double irtotalIlApproxAmountInBaseCurrency2Dbl = irtotalIlApproxAmountInBaseCurrency2.doubleValue();
								iRtotalFmtApproxAmountInBaseCurrency2 = CATFaltFileUtil.getFormattedNumber(irtotalIlApproxAmountInBaseCurrency2Dbl);
							}
						Log.customer.debug("iRtotalFmtApproxAmountInBaseCurrency2 ==> " +iRtotalFmtApproxAmountInBaseCurrency2);
						}
						// End: Mach1 R5.5 (FRD1.5/TD1.3)

						//   LN-ITM-CDISC-PCTX       	X(6) LineItems.TermsDiscountPercent
						// Change by VJS to suit the field width
						/*
						BigDecimal irLnTermsDiscountPercent = null;
						irFmtLnTermsDiscountPercent = null;
						if (IrLineItem2.getFieldValue("TermsDiscountPercent") != null){
							irLnTermsDiscountPercent = (BigDecimal)IrLineItem2.getFieldValue("TermsDiscountPercent");
							double irLnTermsDiscountPercentStr = irLnTermsDiscountPercent.doubleValue();
							Log.customer.debug("irLnTermsDiscountPercentStr is ####, so adding +.0000  ==> " +irLnTermsDiscountPercentStr);
							if (irLnTermsDiscountPercentStr == 0.0) {
								Log.customer.debug("irLnTermsDiscountPercentStr is 0, so adding +.0000  ==> " +irLnTermsDiscountPercentStr);
								irFmtLnTermsDiscountPercent = "+.0000";
							}

							else  {
							Log.customer.debug("else part irLnTermsDiscountPercentStr is not 0, ==> " +irLnTermsDiscountPercentStr);
							irFmtLnTermsDiscountPercent = CATFaltFileUtil.getFormattedNumber(irLnTermsDiscountPercentStr,"000.0000");
							Log.customer.debug("irFmtLnTermsDiscountPercent is not 0   ==> " +irFmtLnTermsDiscountPercent);
							}

						}
						Log.customer.debug("irFmtLnTermsDiscountPercent ==> " +irFmtLnTermsDiscountPercent);
						*/

						BigDecimal irLnTermsDiscountPercent = null;
						irFmtLnTermsDiscountPercent = null;

						irLnTermsDiscountPercent = (BigDecimal)IrLineItem2.getFieldValue("TermsDiscountPercent");
						double irLnTermsDiscountPercentStr = irLnTermsDiscountPercent.doubleValue();

						if (irLnTermsDiscountPercentStr == 0.0) {
							Log.customer.debug("irLnTermsDiscountPercentStr is 0, so adding +.0000  ==> " +irLnTermsDiscountPercentStr);
							irFmtLnTermsDiscountPercent = "+.0000";
						}

						else  {
							Log.customer.debug("else part irLnTermsDiscountPercentStr is not 0, ==> " +irLnTermsDiscountPercentStr);
							irLnTermsDiscountPercent = irLnTermsDiscountPercent.divide(new BigDecimal("100.00"),5, BigDecimal.ROUND_HALF_UP);
							double irLnTermsDiscountPercentStrg = irLnTermsDiscountPercent.doubleValue();


							irFmtLnTermsDiscountPercent = CATFaltFileUtil.getFormattedNumber(irLnTermsDiscountPercentStrg,".0000");
							Log.customer.debug("irFmtLnTermsDiscountPercent is not 0   ==> " +irFmtLnTermsDiscountPercent);
						}


						Log.customer.debug("irFmtLnTermsDiscountPercent ==> " +irFmtLnTermsDiscountPercent);

						//   LN-ITM-CDISC-AMTX  X(17) // LineItems.Accountings.SplitAccountings.InvoiceSplitDiscountDollarAmount.ApproxAmountInBaseCurrency
						BigDecimal iRLnDiscountApproxAmountInBaseCurrency = null;
						iRLnFmtDiscountApproxAmountInBaseCurrency = "";
						double iRLnDiscountApproxAmountInBaseCurrencyStr =0.0;
						if (splitAcc.getDottedFieldValue("InvoiceSplitDiscountDollarAmount.ApproxAmountInBaseCurrency") != null){
							iRLnDiscountApproxAmountInBaseCurrency = (BigDecimal)splitAcc.getDottedFieldValue("InvoiceSplitDiscountDollarAmount.ApproxAmountInBaseCurrency");
							iRLnDiscountApproxAmountInBaseCurrencyStr = iRLnDiscountApproxAmountInBaseCurrency.doubleValue();

						}
						iRLnFmtDiscountApproxAmountInBaseCurrency = CATFaltFileUtil.getFormattedNumber(iRLnDiscountApproxAmountInBaseCurrencyStr);
						Log.customer.debug("iRLnFmtDiscountApproxAmountInBaseCurrency ==> " +iRLnFmtDiscountApproxAmountInBaseCurrency);



						//   FGN-LN-ITM-AMTX X(17)  LineItems. Accountings.SplitAccountings.Amount.Amount
						// Change by VJS to provide the amount only if the currency is not USD

						/*
						BigDecimal iRLnSplitAccountingsAmountAmount = null;
						iRLnFmtSplitAccountingsAmountAmount = "";
						double iRLnSplitAccountingsAmountAmountStr = 0.0;
						if (splitAcc.getDottedFieldValue("Amount.Amount") != null){
							iRLnSplitAccountingsAmountAmount = (BigDecimal)splitAcc.getDottedFieldValue("Amount.Amount");
							iRLnSplitAccountingsAmountAmountStr = iRLnSplitAccountingsAmountAmount.doubleValue();

						}
						iRLnFmtSplitAccountingsAmountAmount = CATFaltFileUtil.getFormattedNumber(iRLnSplitAccountingsAmountAmountStr);
						Log.customer.debug("iRLnFmtSplitAccountingsAmountAmount ==> " +iRLnFmtSplitAccountingsAmountAmount);
						*/

						BigDecimal iRLnSplitAccountingsAmountAmount = null;
						iRLnFmtSplitAccountingsAmountAmount = "";
						double iRLnSplitAccountingsAmountAmountStr = 0.0;
						if( !iRCurrency.equals("USD") )
						{
							if (splitAcc.getDottedFieldValue("Amount.Amount") != null){
							iRLnSplitAccountingsAmountAmount = (BigDecimal)splitAcc.getDottedFieldValue("Amount.Amount");
							iRLnSplitAccountingsAmountAmountStr = iRLnSplitAccountingsAmountAmount.doubleValue();
							}
							iRLnFmtSplitAccountingsAmountAmount = CATFaltFileUtil.getFormattedNumber(iRLnSplitAccountingsAmountAmountStr);
							Log.customer.debug("iRLnFmtSplitAccountingsAmountAmount ==> " +iRLnFmtSplitAccountingsAmountAmount);
						}
						else
						{
							iRLnFmtSplitAccountingsAmountAmount = "+0.00            ";
						}
						// Start: Mach1 R5.5 (FRD1.5/TD1.3)
						iRLntotalStrFmtSplitAccountingsAmountAmount = "";
						double iRLntotalSplitAccountingsAmountAmountStr = 0.0;

						if (iRFmtinvcLnTyp.equals("002") || iRFmtinvcLnTyp.equals("096") || iRFmtinvcLnTyp.equals("003"))
						{
							Log.customer.debug(" Enter tax line loop" );
							if( !iRCurrency.equals("USD") )
							{
								Log.customer.debug(" Enter tax line loop curr not usd" );
								if (splitAcc.getDottedFieldValue("Amount.Amount") != null){
								iRLnSplitAccountingsAmountAmount = (BigDecimal)splitAcc.getDottedFieldValue("Amount.Amount");
								Log.customer.debug("iRLnSplitAccountingsAmountAmount ==> " +iRLnSplitAccountingsAmountAmount);
								iRLntotalFmtSplitAccountingsAmountAmount=iRLntotalFmtSplitAccountingsAmountAmount.add(iRLnSplitAccountingsAmountAmount);
								Log.customer.debug("iRLntotalFmtSplitAccountingsAmountAmount ==> " +iRLntotalFmtSplitAccountingsAmountAmount);
								iRLntotalSplitAccountingsAmountAmountStr = iRLntotalFmtSplitAccountingsAmountAmount.doubleValue();
								Log.customer.debug("iRLntotalSplitAccountingsAmountAmountStr ==> " +iRLntotalSplitAccountingsAmountAmountStr);
								}
								iRLntotalStrFmtSplitAccountingsAmountAmount = CATFaltFileUtil.getFormattedNumber(iRLntotalSplitAccountingsAmountAmountStr);
								Log.customer.debug("iRLntotalStrFmtSplitAccountingsAmountAmount ==> " +iRLntotalStrFmtSplitAccountingsAmountAmount);
							}
							else
							{
								iRLntotalStrFmtSplitAccountingsAmountAmount = "+0.00            ";
								Log.customer.debug("iRLntotalStrFmtSplitAccountingsAmountAmount ==> " +iRLntotalStrFmtSplitAccountingsAmountAmount);
							}

						}
						// End: Mach1 R5.5 (FRD1.5/TD1.3)

						//   FGN-UNIT-PRCX   X(17) LineItems. Accountings.SplitAccountings.Amount.Amount
						// Change by VJS to suit provide the amount only if the Currency is not USD
						/*
						BigDecimal iRLnSplitAccountingsAmountAmount2 = null;
						iRLnFmtSplitAccountingsAmountAmount2 = "";
						double iRLnSplitAccountingsAmountAmountStr2 =0.0;
						if (splitAcc.getDottedFieldValue("Amount.Amount") != null){
							iRLnSplitAccountingsAmountAmount2 = (BigDecimal)splitAcc.getDottedFieldValue("Amount.Amount");

							iRLnSplitAccountingsAmountAmountStr2 = iRLnSplitAccountingsAmountAmount2.doubleValue();

						}
						iRLnFmtSplitAccountingsAmountAmount2 = CATFaltFileUtil.getFormattedNumber(iRLnSplitAccountingsAmountAmountStr2);
						Log.customer.debug("iRLnFmtSplitAccountingsAmountAmount2 ==> " +iRLnFmtSplitAccountingsAmountAmount2);
						*/

						BigDecimal iRLnSplitAccountingsAmountAmount2 = null;
						iRLnFmtSplitAccountingsAmountAmount2 = "";
						double iRLnSplitAccountingsAmountAmountStr2 =0.0;
						if( !iRCurrency.equals("USD") )
						{
							if (splitAcc.getDottedFieldValue("Amount.Amount") != null){
							iRLnSplitAccountingsAmountAmount2 = (BigDecimal)splitAcc.getDottedFieldValue("Amount.Amount");
							iRLnSplitAccountingsAmountAmountStr2 = iRLnSplitAccountingsAmountAmount2.doubleValue();
							}
							iRLnFmtSplitAccountingsAmountAmount2 = CATFaltFileUtil.getFormattedNumber(iRLnSplitAccountingsAmountAmountStr2, "0000000000.00000");
							Log.customer.debug("iRLnFmtSplitAccountingsAmountAmount2 ==> " +iRLnFmtSplitAccountingsAmountAmount2);
						}
						else
						{
							iRLnFmtSplitAccountingsAmountAmount2 = "+0.00            ";
						}
						// Start: Mach1 R5.5 (FRD1.5/TD1.3)
						iRLntotalStrFmtSplitAccountingsAmountAmount2 = "";
						double iRLntotalSplitAccountingsAmountAmountStr2 = 0.0;

						if (iRFmtinvcLnTyp.equals("002") || iRFmtinvcLnTyp.equals("096") || iRFmtinvcLnTyp.equals("003"))
						{
							Log.customer.debug(" Enter tax line loop 2" );
							if( !iRCurrency.equals("USD") )
							{
								Log.customer.debug(" Enter tax line loop 2 currency not usd" );
								if (splitAcc.getDottedFieldValue("Amount.Amount") != null){
								iRLnSplitAccountingsAmountAmount2 = (BigDecimal)splitAcc.getDottedFieldValue("Amount.Amount");
								Log.customer.debug("iRLnSplitAccountingsAmountAmount2 ==> " +iRLnSplitAccountingsAmountAmount2);
								iRLntotalFmtSplitAccountingsAmountAmount2=iRLntotalFmtSplitAccountingsAmountAmount2.add(iRLnSplitAccountingsAmountAmount2);
								Log.customer.debug("iRLntotalFmtSplitAccountingsAmountAmount2 ==> " +iRLntotalFmtSplitAccountingsAmountAmount2);
								iRLntotalSplitAccountingsAmountAmountStr2 = iRLntotalFmtSplitAccountingsAmountAmount2.doubleValue();
								Log.customer.debug("iRLntotalSplitAccountingsAmountAmountStr2 ==> " +iRLntotalSplitAccountingsAmountAmountStr2);
								}
								iRLntotalStrFmtSplitAccountingsAmountAmount2 = CATFaltFileUtil.getFormattedNumber(iRLntotalSplitAccountingsAmountAmountStr2, "0000000000.00000");
								Log.customer.debug("iRLntotalStrFmtSplitAccountingsAmountAmount2 ==> " +iRLntotalStrFmtSplitAccountingsAmountAmount2);
							}
							else
							{
								iRLntotalStrFmtSplitAccountingsAmountAmount2 = "+0.00            ";
								Log.customer.debug("iRLntotalStrFmtSplitAccountingsAmountAmount2 ==> " +iRLntotalStrFmtSplitAccountingsAmountAmount2);
							}

						}
						// End: Mach1 R5.5 (FRD1.5/TD1.3)

						//   INVC-TX-CD          	X(2) LineItems.ERPTaxCode
						String irLnERPTaxCode = "";
						irFmtERPTaxCode = "";
						if (IrLineItem2.getFieldValue("ERPTaxCode") != null){
							irLnERPTaxCode = IrLineItem2.getFieldValue("ERPTaxCode").toString().toUpperCase();
							irFmtERPTaxCode = CATFaltFileUtil.getFormattedTxt(irLnERPTaxCode,2);
						}
						else
						{
							irFmtERPTaxCode = "  ";
						}
						Log.customer.debug("irFmtERPTaxCode ==> " +irFmtERPTaxCode);



						//   INVC-TX-PCTX          	X(7) LineItems.IRTaxRate
						BigDecimal irLnIRTaxRate =null;
						irFmtIRTaxRate = null;
						if (IrLineItem2.getFieldValue("IRTaxRate") != null){
							irLnIRTaxRate = (BigDecimal)IrLineItem2.getFieldValue("IRTaxRate");
							double irLnIRTaxRateDbl = irLnIRTaxRate.doubleValue();
							if (irLnIRTaxRateDbl == 0.0) {
								irFmtIRTaxRate = "+.00000";
							}
							else {
							//String irLnIRTaxRateStr = Double.toString(irLnIRTaxRateDbl);
							irFmtIRTaxRate = CATFaltFileUtil.getFormattedNumber(irLnIRTaxRateDbl,".00000");
							}
						}
						Log.customer.debug("irFmtIRTaxRate ==> " +irFmtIRTaxRate);

						//   ACCT-NO-FAC-CD  X(2) LineItems.Accountings.SplitAccountings.AccountingFacility
						String iRLnSplitAccountingsAccountingFacility = (String)splitAcc.getFieldValue("AccountingFacility");
						iRLnFmtSplitAccountingsAccountingFacility = null;
						if (iRLnSplitAccountingsAccountingFacility != null){
							// Change by VJS, commented out the below line and added new line for Upper case conversion
							//iRLnFmtSplitAccountingsAccountingFacility = CATFaltFileUtil.getFormattedTxt(iRLnSplitAccountingsAccountingFacility,2);
							iRLnFmtSplitAccountingsAccountingFacility = CATFaltFileUtil.getFormattedTxt(iRLnSplitAccountingsAccountingFacility.toUpperCase(),2);
						}
						Log.customer.debug("iRLnFmtSplitAccountingsAccountingFacility ==> " +iRLnFmtSplitAccountingsAccountingFacility);



						//   CTL-ACCT-NO          	X(5)  LineItems.Accountings.SplitAccountings.Department
						String iRLnSplitAccountingsDepartment = "";
						iRLnFmtSplitAccountingsDepartment = null;
						if (splitAcc.getFieldValue("Department") != null){
							iRLnSplitAccountingsDepartment = (String)splitAcc.getFieldValue("Department");
							// Change by VJS, commented out the below line and added new line for Upper case conversion
							//iRLnFmtSplitAccountingsDepartment = CATFaltFileUtil.getFormattedTxt(iRLnSplitAccountingsDepartment,5);
							iRLnFmtSplitAccountingsDepartment = CATFaltFileUtil.getFormattedTxt(iRLnSplitAccountingsDepartment.toUpperCase(),5);
						}
						Log.customer.debug("iRLnFmtSplitAccountingsDepartment ==> " +iRLnFmtSplitAccountingsDepartment);


						//   SUB-ACCT-NO    X(3) LineItems.Accountings.SplitAccountings.Division

						String iRLnSplitAccountingsDivision = "";
						iRLnFmtSplitAccountingsDivision = null;
						if (splitAcc.getFieldValue("Division")!= null){
							iRLnSplitAccountingsDivision = (String)splitAcc.getFieldValue("Division");
							// Change by VJS, commented out the below line and added new line for Upper case conversion
							//iRLnFmtSplitAccountingsDivision = CATFaltFileUtil.getFormattedTxt(iRLnSplitAccountingsDivision,3);
							iRLnFmtSplitAccountingsDivision = CATFaltFileUtil.getFormattedTxt(iRLnSplitAccountingsDivision.toUpperCase(),3);
						}
						Log.customer.debug("iRLnFmtSplitAccountingsDivision ==> " +iRLnFmtSplitAccountingsDivision);


						//   SUB-SUB-ACCT-NO   X(2)  LineItems.Accountings.SplitAccountings.Section

						String iRLnSplitAccountingsSection = "";
						iRLnFmtSplitAccountingsSection = null;
						if (splitAcc.getFieldValue("Section") != null){
							iRLnSplitAccountingsSection = (String)splitAcc.getFieldValue("Section");
							// Change by VJS, commented out the below line and added new line for Upper case conversion
							//iRLnFmtSplitAccountingsSection = CATFaltFileUtil.getFormattedTxt(iRLnSplitAccountingsSection,2);
							iRLnFmtSplitAccountingsSection = CATFaltFileUtil.getFormattedTxt(iRLnSplitAccountingsSection.toUpperCase(),2);
						}
						Log.customer.debug("iRLnFmtSplitAccountingsSection ==> " +iRLnFmtSplitAccountingsSection);


						//   EXP-ACCT-NO   X(4) LineItems.Accountings.SplitAccountings.ExpenseAccount
						String iRLnSplitAccountingsExpenseAccount = "  " ;
						iRLnFmtSplitAccountingsExpenseAccount = null;
						if (splitAcc.getFieldValue("ExpenseAccount") != null){
							iRLnSplitAccountingsExpenseAccount = (String)splitAcc.getFieldValue("ExpenseAccount");

						}
						iRLnFmtSplitAccountingsExpenseAccount = CATFaltFileUtil.getFormattedTxt(iRLnSplitAccountingsExpenseAccount,4);
						Log.customer.debug("iRLnFmtSplitAccountingsExpenseAccount ==> " +iRLnFmtSplitAccountingsExpenseAccount);


						//   ACCTG-ORD-NO X(5) LineItems.Accountings.SplitAccountings.Order
						// Change by VJS to fill the zeros if value is blank or null
						/*
						String iRLnSplitAccountingsOrder = "  ";
						iRLnFmtSplitAccountingsOrder = null;
						if (splitAcc.getFieldValue("Order") != null){
							iRLnSplitAccountingsOrder = (String)splitAcc.getFieldValue("Order");

						}
						iRLnFmtSplitAccountingsOrder = CATFaltFileUtil.getFormattedTxt(iRLnSplitAccountingsOrder,5);
						Log.customer.debug("iRLnFmtSplitAccountingsOrder ==> " +iRLnFmtSplitAccountingsOrder);
						*/

						String iRLnSplitAccountingsOrder = "  ";
						iRLnFmtSplitAccountingsOrder = null;
						if (splitAcc.getFieldValue("Order") != null){
							iRLnSplitAccountingsOrder = (String)splitAcc.getFieldValue("Order");
							iRLnFmtSplitAccountingsOrder = CATFaltFileUtil.getFormattedTxt(iRLnSplitAccountingsOrder,5);
						}
						else
						{
							iRLnFmtSplitAccountingsOrder = "00000";
						}

						Log.customer.debug("iRLnFmtSplitAccountingsOrder ==> " +iRLnFmtSplitAccountingsOrder);

						//   ACCTG-DIST-QUAL   X(3) LineItems.Accountings.SplitAccountings.Misc
												/*
						String iRLnSplitAccountingsMisc = "   ";
						iRLnFmtSplitAccountingsMisc = "";
						if (splitAcc.getFieldValue("Misc") != null){
							iRLnSplitAccountingsMisc = (String)splitAcc.getFieldValue("Misc");

						}
						iRLnFmtSplitAccountingsMisc = CATFaltFileUtil.getFormattedTxt(iRLnSplitAccountingsMisc.toUpperCase(),3);

						Log.customer.debug("iRLnFmtSplitAccountingsMisc ==> " +iRLnFmtSplitAccountingsMisc);
						*/

						String iRLnSplitAccountingsMisc = "   ";
						iRLnFmtSplitAccountingsMisc = "";
						if (splitAcc.getFieldValue("Misc") != null){
							iRLnSplitAccountingsMisc = (String)splitAcc.getFieldValue("Misc");
							iRLnFmtSplitAccountingsMisc = CATFaltFileUtil.getFormattedTxt(iRLnSplitAccountingsMisc.toUpperCase(),3);
						}
						else
						{
							iRLnFmtSplitAccountingsMisc = "000";
						}
						Log.customer.debug("iRLnFmtSplitAccountingsMisc ==> " +iRLnFmtSplitAccountingsMisc);

												// To send absolute value of qty. Change by VJS
						//   INV-QTY  X(10) Always send |1|
						//String iRLnFmtInvQty = "1         ";
						//Log.customer.debug("iRLnFmtInvQty ==> " +iRLnFmtInvQty);

						BigDecimal irIlQuantity2 = (BigDecimal)splitAcc.getFieldValue("Quantity");
						double irIlQuantity2Dbl = irIlQuantity2.doubleValue();
						//irIlQuantity2 = irIlQuantity2.abs();
						//String iRLnFmtInvQty = null;
						//iRLnFmtInvQty = BigDecimalFormatter.getStringValue(irIlQuantity2);
					    String iRLnFmtInvQty = CATFaltFileUtil.getFormattedNumber(irIlQuantity2Dbl,"000000000");
						Log.customer.debug("iRLnFmtInvQty ==> " +iRLnFmtInvQty);

						//   INV-UM    X(4) Always send '+058'.
						String iRLnFmtInvUm = "+058";
						Log.customer.debug("iRLnFmtInvUm ==> " +iRLnFmtInvUm);

						//   CNVRT-INV-QTY   X(15)  This will always be 1 since we will not be converting this.
						//double iRLnCnvrtInvQty = 1.0;
						//iRLnFmtCnvrtInvQty = CATFaltFileUtil.getFormattedNumber(iRLnCnvrtInvQty,"0.0000");
						iRLnFmtCnvrtInvQty ="+1.0000        ";
						Log.customer.debug("iRLnFmtCnvrtInvQty ==> " +iRLnFmtCnvrtInvQty);

						//   PO-TX-CD          	X(2) LineItems.TaxCode.UniqueName

						irLnFmtTaxCodeUniqueName = "";
						if (IrLineItem2.getDottedFieldValue("TaxCode") != null){
							String irLnTaxCodeUniqueName = (String)IrLineItem2.getDottedFieldValue("TaxCode.UniqueName");
							irLnFmtTaxCodeUniqueName = CATFaltFileUtil.getFormattedTxt(irLnTaxCodeUniqueName,2);
						}
						Log.customer.debug("irLnFmtTaxCodeUniqueName ==> " +irLnFmtTaxCodeUniqueName);

						//   PO-TX-PCTX          	X(7) LineItems.TaxRate

						irLnFmtTaxRate = null;
						if (IrLineItem2.getDottedFieldValue("TaxRate") != null){
							BigDecimal irLnTaxRate =  (BigDecimal)IrLineItem2.getDottedFieldValue("TaxRate");
							double irLnTaxRateDbl = irLnTaxRate.doubleValue();
							if (irLnTaxRateDbl == 0.0) {
								Log.customer.debug("irLnTaxRateDbl is 0, so adding +.00000  ==> " +irLnTaxRateDbl);
								irLnFmtTaxRate = "+.00000";
							}

							else  {
							// Below change by VJS to have percentage tax
							// String irLnTaxRateStr = Double.toString(irLnTaxRateDbl);
							//irLnFmtTaxRate = CATFaltFileUtil.getFormattedNumber(irLnTaxRateDbl,"0.00000");
							irLnTaxRate = irLnTaxRate.divide(new BigDecimal("100.00"),5, BigDecimal.ROUND_HALF_UP);
							double irLnTaxRateDble = irLnTaxRate.doubleValue();
							irLnFmtTaxRate = CATFaltFileUtil.getFormattedNumber(irLnTaxRateDble,".00000");
							Log.customer.debug("irLnTaxRateDbl is not 0   ==> " +irLnTaxRateDbl);
							}

						}
						Log.customer.debug("irLnFmtTaxRate ==> " +irLnFmtTaxRate);

						//   LN-ITM-DESC   X(56)  Always send 'MSC PURCHASE ORDER'
						String iRLnDesc = "MSC PURCHASE ORDER";
						irLnFmtDesc = CATFaltFileUtil.getFormattedTxt(iRLnDesc,56);

						Log.customer.debug("irLnFmtDesc ==> " +irLnFmtDesc);

						//   LAST-UPDT-TS		X(26) ControlDate
						Date iRControlDate =  (Date)invrecon.getFieldValue("ControlDate");
						Log.customer.debug("iRControlDate ==> " +iRControlDate);
						iRFmtControlDate = CATFaltFileUtil.getFormattedDate(iRControlDate,"yyyy-MM-dd-hh.mm.ss");
						// Change by VJS per Prod data
						iRFmtControlDate = iRFmtControlDate + ".000000";
						Log.customer.debug("iRFmtControlDate ==> " +iRFmtControlDate);


						//   FILLER			X(24) SPACES
						String filler = " ";
						fmtFiller = CATFaltFileUtil.getFormattedTxt(filler,24);

						isPushed = false;

						Log.customer.debug("IR data writing to file  ==> ");
						// Start: Mach1 R5.5 (FRD1.5/TD1.3)
						if (!iRFmtinvcLnTyp.equals("002") && !iRFmtinvcLnTyp.equals("096") && !iRFmtinvcLnTyp.equals("003"))
						{
							Log.customer.debug("Enter Non Tax Line  ==> ");
							String iRData = iRFmtSupplierLocation +"~|"+ iRFmtBlockStampDate+"~|"+iRFmtTotalCost+"~|"+iRFmtDiscountTotalCost+"~|"+iRFmtiRTotalCostMinusDiscount+"~|"+iRFmtCurrency + "~|"+ iRFmtFgnTotalCost +"~|"+ IRFmtUSExchangeRate +"~|"+IRFmtCurrencyTimeUpdated+ "~|"+iRFmtSupplierInvoiceDate+"~|"+iRFmtInvoiceNumber+"~|"+iRFmtBtchNO+"~|"+iRFmtEntMethInd+"~|"+iRFmtinvcLnTyp+"~|"+iRFmtApInvcLnNo+"~|"+iRFmtRcvgFacCd+"~|"+irFmtShpDt+"~|"+iRFmtSuppShpRef1+"~|"+iRFmtSuppShpRef2+"~|"+iRFmtPONumber+"~|"+iRFmtCatIdNo20+"~|"+iRFmtCatIdClsCD+"~|"+irFmtIlQuantity+"~|"+iRFmtPrcxUm1+"~|"+iRFmtApproxAmountInBaseCurrency+"~|"+iRFmtApproxAmountInBaseCurrency2+"~|"+irFmtLnTermsDiscountPercent+"~|"+iRLnFmtDiscountApproxAmountInBaseCurrency+"~|"+iRLnFmtSplitAccountingsAmountAmount+"~|"+iRLnFmtSplitAccountingsAmountAmount2+"~|"+irFmtERPTaxCode+"~|"+irFmtIRTaxRate+"~|"+iRLnFmtSplitAccountingsAccountingFacility+"~|"+iRLnFmtSplitAccountingsDepartment+"~|"+iRLnFmtSplitAccountingsDivision+"~|"+iRLnFmtSplitAccountingsSection+"~|"+iRLnFmtSplitAccountingsExpenseAccount+"~|"+iRLnFmtSplitAccountingsOrder+"~|"+iRLnFmtSplitAccountingsMisc+"~|"+iRLnFmtInvQty+"~|"+iRLnFmtInvUm+"~|"+iRLnFmtCnvrtInvQty+"~|"+irLnFmtTaxCodeUniqueName+"~|"+irLnFmtTaxRate+"~|"+irLnFmtDesc+"~|"+iRFmtControlDate+"~|"+fmtFiller+"~|";

							Log.customer.debug("IR data writing to file  ==> " +iRData);
							outPW_FlatFile.write(iRData);
							Log.customer.debug("New Line writing to file  ==> ");
							outPW_FlatFile.write("\n");
							//iSpAcct+= irSplitaccSize;
							iSpAcct= iSpAcct+1;
							Log.customer.debug("iSpAcct  ==> " +iSpAcct);
						}
                        if (i+1== lineCount)
                        {
							if (!s.hasNext() && (iRFmtinvcLnTyp.equals("002") || iRFmtinvcLnTyp.equals("096") || iRFmtinvcLnTyp.equals("003")))
							{
								Log.customer.debug("Enter Tax Line  ==> ");
								String iRData = iRFmtSupplierLocation +"~|"+ iRFmtBlockStampDate+"~|"+iRFmtTotalCost+"~|"+iRFmtDiscountTotalCost+"~|"+iRFmtiRTotalCostMinusDiscount+"~|"+iRFmtCurrency + "~|"+ iRFmtFgnTotalCost +"~|"+ IRFmtUSExchangeRate +"~|"+IRFmtCurrencyTimeUpdated+ "~|"+iRFmtSupplierInvoiceDate+"~|"+iRFmtInvoiceNumber+"~|"+iRFmtBtchNO+"~|"+iRFmtEntMethInd+"~|"+iRFmtinvcLnTyp+"~|"+iRFmtApInvcLnNo+"~|"+iRFmtRcvgFacCd+"~|"+irFmtShpDt+"~|"+iRFmtSuppShpRef1+"~|"+iRFmtSuppShpRef2+"~|"+iRFmtPONumber+"~|"+iRFmtCatIdNo20+"~|"+iRFmtCatIdClsCD+"~|"+irFmtIlQuantity+"~|"+iRFmtPrcxUm1+"~|"+iRtotalFmtApproxAmountInBaseCurrency+"~|"+iRtotalFmtApproxAmountInBaseCurrency2+"~|"+irFmtLnTermsDiscountPercent+"~|"+iRLnFmtDiscountApproxAmountInBaseCurrency+"~|"+iRLntotalStrFmtSplitAccountingsAmountAmount+"~|"+iRLntotalStrFmtSplitAccountingsAmountAmount2+"~|"+irFmtERPTaxCode+"~|"+irFmtIRTaxRate+"~|"+iRLnFmtSplitAccountingsAccountingFacility+"~|"+iRLnFmtSplitAccountingsDepartment+"~|"+iRLnFmtSplitAccountingsDivision+"~|"+iRLnFmtSplitAccountingsSection+"~|"+iRLnFmtSplitAccountingsExpenseAccount+"~|"+iRLnFmtSplitAccountingsOrder+"~|"+iRLnFmtSplitAccountingsMisc+"~|"+iRLnFmtInvQty+"~|"+iRLnFmtInvUm+"~|"+iRLnFmtCnvrtInvQty+"~|"+irLnFmtTaxCodeUniqueName+"~|"+irLnFmtTaxRate+"~|"+irLnFmtDesc+"~|"+iRFmtControlDate+"~|"+fmtFiller+"~|";

								Log.customer.debug("IR data writing to file  ==> " +iRData);
								outPW_FlatFile.write(iRData);
								Log.customer.debug("Tax Line writing to file  ==> ");
								outPW_FlatFile.write("\n");
								//iSpAcct+= irSplitaccSize;
								iSpAcct= iSpAcct+1;
								Log.customer.debug("iSpAcct  ==> " +iSpAcct);
							}
                        }
						// End: Mach1 R5.5 (FRD1.5/TD1.3)

							}

								}

							} //if (irSplitaccSize > 0)

							} // if (irAccounting!=null)

								} //for (int i =0; i<IrLineItemvector;i++)
						} //if (lineCount > 0)
						String ctrlDataForIr = getControlFileData(invrecon,controlid);
						Log.customer.debug("write to control file");
						outPW_CTRLFlatFile.write(ctrlDataForIr);
						outPW_CTRLFlatFile.write("\n");
						isPushed = true;
						if (isPushed & (ctrlDataForIr != null)) {
							//Log.customer.debug("**********Not set to Complted until testing over  ");
						   invrecon.setFieldValue("ActionFlag", "Completed");
							pushedCount++;
						}
						else
							invrecon.setFieldValue("ActionFlag", "InProcess");



					}  // if(invrecon != null)
					commitCount++;
					if(commitCount == 50)
					   {
							Log.customer.debug("**********Commiting IR Records*******  ",commitCount);
							Base.getSession().transactionCommit();
							commitCount = 0;
						}
						continue;

			} //  while
			Base.getSession().transactionCommit();


		} catch (Exception e) {
			isPushed = false;
			invrecon.setFieldValue("ActionFlag", "InProcess");

			//add message


			message.append("Task start time : "+ startTime);
			message.append("\n");
			message.append("Task end time : " + endTime);
			message.append("\n");
			message.append("No of records pushed : "+ pushedCount);
			message.append("\n");
			message.append("No of records queued  :"+ (resultCount - pushedCount));
			message.append("\n");
			message.append("CAPSInvoiceReconciliationPush Failed - Exception details below");
			message.append("\n");
			message.append(e.toString());
			mailSubject = "CAPSInvoiceReconciliationPush Task Failed";
			Log.customer.debug("%s: Inside Exception message "+ message.toString());

			Log.customer.debug(e);
		}
		finally {
			if (outPW_CTRLFlatFile != null)  {
				outPW_CTRLFlatFile.flush();
				outPW_CTRLFlatFile.close();}

			if (outPW_FlatFile != null)  {
				outPW_FlatFile.flush();
				outPW_FlatFile.close();
				//Change made by Soumya begins
				Log.customer.debug("CATCAPSInvoiceFlatFilePush:Copying Data file to achive.");
				CATFaltFileUtil.copyFile(flatFilePath, archiveFileDataPath);
				Log.customer.debug("CATCAPSInvoiceFlatFilePush:Coppied Data file to achive.");
				Log.customer.debug("CATCAPSInvoiceFlatFilePush:Copying Control file to achive.");
				CATFaltFileUtil.copyFile(controlFlatFilePath, archiveFileCtrlPath);
				Log.customer.debug("CATCAPSInvoiceFlatFilePush:Coppied Control file to achive.");
				//Change made by Soumya end
				try {
					File f=new File(triggerFile);
					if(!f.exists()){
						 f.createNewFile();
						Log.customer.debug("triggerFile has been created "+ message.toString());
						  }
					 else {
						Log.customer.debug("triggerFile allready exit. "+ message.toString());
					 }
				} catch (IOException e1) {
					Log.customer.debug("triggerFile allready exit. "+ e1);
				}

			}

			Log.customer.debug("%s: Inside Finally ");
			message.append("Task start time : "+ startTime);
			Log.customer.debug("%s: Inside Finally added start time");
			message.append("\n");
			endTime = DateFormatter.getStringValue(new ariba.util.core.Date(), "EEE MMM d hh:mm:ss a z yyyy", TimeZone.getTimeZone("CST"));
			message.append("Task end time : " + endTime);
			message.append("\n");
			message.append("Records to be pushed : "+ resultCount);
			message.append("\n");
			message.append("No. of records successfully pushed : "+ pushedCount);
			message.append("\n");
			Log.customer.debug("%s: Inside Finally message "+ message.toString());

			// Sending email
			CatEmailNotificationUtil.sendEmailNotification(mailSubject, message.toString(), "cat.java.emails", "CAPSIRPushNotify");
			message = null;
			pushedCount =0;
			resultCount =0;



		}


	}



    void GenerateCapsLineNumber (InvoiceReconciliation inv) {
		InvoiceSplitDiscountDollarAmount = Constants.ZeroBigDecimal;
		TotalInvoiceAmountMinusDiscount = Constants.ZeroBigDecimal;
		int iLineNo = 1;
		int iLineType = 0;

		Log.customer.debug("IR Object: " + inv );

		for(Iterator i = inv.getLineItemsIterator(); i.hasNext();) {
			InvoiceReconciliationLineItem irLine = (InvoiceReconciliationLineItem)i.next();

			BigDecimal totSplitAmtToCompare = Constants.ZeroBigDecimal;

			if (irLine == null) continue;

			if (irLine.getFieldValue("LineType") != null)
				iLineType = ( (Integer)irLine.getDottedFieldValue("LineType.Category") ).intValue();

			Log.customer.debug(" IRLI #"+irLine.getNumberInCollection() +"LineType is. " + iLineType );

			for(Iterator s= irLine.getAccountings().getSplitAccountingsIterator(); s.hasNext();) {
				splitAcc = (SplitAccounting) s.next();

				if (splitAcc != null) {
					if (IsForeign) {
						Log.customer.debug("IsForeign is true " );
						BigDecimal splitAccBaseCurrValue = (BigDecimal) splitAcc.getAmount().getApproxAmountInBaseCurrency();

						totSplitAmtToCompare = totSplitAmtToCompare.add(splitAccBaseCurrValue);

						Log.customer.debug(" totSplitAmtToCompare"+totSplitAmtToCompare);
					}

					Log.customer.debug("Inside the Loop and in If");

					iCAPSLineNo = new Integer(iLineNo);
					sCAPSLineNo = new String ( iCAPSLineNo.toString() );
					Log.customer.debug("Setting CapsLineNumber as "+sCAPSLineNo);
					splitAcc.setFieldValue("CapsLineNumber", sCAPSLineNo );
				iLineNo ++;	 }
			}
		}
	}

	public String getControlFileData(InvoiceReconciliation invrecon, String controlid) throws Exception
	{
		Log.customer.debug("Inside Control file data generation ... invrecon .."+invrecon );
		Log.customer.debug("Inside Control file data generation ... controlid .."+controlid );
		String topicname1 = new String("ControlObjectPush");
		Partition p2 = Base.getService().getPartition("None");
		ClusterRoot cluster = null;


		String ctrlQuery = new String( "Select from cat.core.ControlPullObject where UniqueName = '"+controlid + "'" );
		Log.customer.debug("iRQuery ==> " + ctrlQuery);
		aqlctrlQuery = AQLQuery.parseQuery(ctrlQuery);
		ctrlResultSet = Base.getService().executeQuery(aqlctrlQuery, options);

		if (ctrlResultSet.getErrors() != null)
		     Log.customer.debug("ERROR GETTING RESULTS in aqlctrlQuery ");

		int totalNumberOfCtrl = ctrlResultSet.getSize();

		Log.customer.debug("Inside getControlFileData ... totalNumberOfCtrl "+totalNumberOfCtrl );

	//	Base.getSession().transactionBegin();


		if (totalNumberOfCtrl == 0) {
		        cluster = (ClusterRoot)ClusterRoot.create("cat.core.ControlPullObject", p2);
		        Log.customer.debug("Inside else part getControlFileData ... cluster "+cluster );
		}
		else
			while(ctrlResultSet.next()){
			cluster = (ClusterRoot)ctrlResultSet.getBaseId("ControlPullObject").get();
			Log.customer.debug("Inside else part getControlFileData ... cluster "+cluster );
			}
		Log.customer.debug("Inside getControlFileData ... controlid "+controlid );
		cluster.setFieldValue("UniqueName", controlid);
		Log.customer.debug("Inside getControlFileData ... datetimezone "+datetimezone );
		cluster.setFieldValue("ControlDate", datetimezone);
		Log.customer.debug("Inside getControlFileData ... MSC_CAPS_INVOICES ");
		cluster.setFieldValue("InterfaceName", "MSC_CAPS_INVOICES");
		Log.customer.debug("Inside getControlFileData SourceSystem..........");
		cluster.setFieldValue("SourceSystem", "Ariba_vcsv1_pcsv1");
		Log.customer.debug("5..........");
		cluster.setFieldValue("SourceFacility", "        ");	//8 Spaces
		Log.customer.debug("6..........");
		cluster.setFieldValue("TargetSystem", "CAPS");
		Log.customer.debug("7..........");
		cluster.setFieldValue("TargetFacility", "CAPS");
		Log.customer.debug("8..........");
		cluster.setFieldValue("RecordCount", new Integer(1));
		String ctrlData = "";

		if (bdTotCost != null)
		{
			bdTotCost =  bdTotCost.setScale(2, java.math.BigDecimal.ROUND_HALF_UP);
			cluster.setFieldValue("TotalAmount", bdTotCost);
			cluster.save();
		}
		Log.customer.debug("Area2 .........."+ iSpAcct);
		cluster.setFieldValue("Area2", new Integer(iSpAcct));	//Sum of splitaccountings
		cluster.save();
		Log.customer.debug("Area2.......... saved");
		cluster.setFieldValue("Area3", "                                             ");	//48 Spaces
		Log.customer.debug("Area2..........");
		cluster.save();
		Log.customer.debug("saved ..........");

		//Base.getSession().transactionCommit();
		//Log.customer.debug("after  transactionCommit() ..........");

		//if(cluster.isSaved() && cluster != null) {
		try
		{
			//UNIQUE-ID(30)   UniqueName
			String uniqueNameCRTLObj = (String)cluster.getFieldValue("UniqueName");
			String uniqueNameCRTLObjFmt =  CATFaltFileUtil.getFormattedTxt(uniqueNameCRTLObj.toUpperCase(),30);
			Log.customer.debug("uniqueNameCRTLObjFmt  ...."+ uniqueNameCRTLObjFmt);
			//DATE-TIME(26) ControlDate
			Date controlDateCRTLObj = (Date)cluster.getFieldValue("ControlDate");
			String CcontrolDateCRTLObjFmt =  CATFaltFileUtil.getFormattedDate(controlDateCRTLObj,"yyyy-MM-dd-hh.mm.ss");
			// Added below line by VJS to suit the need as per functional doc - Control file changes
			CcontrolDateCRTLObjFmt = CcontrolDateCRTLObjFmt + ".000000";
			Log.customer.debug("controlDateCRTLObj  ...."+ controlDateCRTLObj);


			//INTERFACE-NAME(80)  InterfaceName
			String interfaceNameCRTLObj = (String)cluster.getFieldValue("InterfaceName");
			String interfaceNameCRTLObjFmt =  CATFaltFileUtil.getFormattedTxt(interfaceNameCRTLObj,80);
			Log.customer.debug("interfaceNameCRTLObjFmt  ...."+ interfaceNameCRTLObjFmt);

			//SOURCE-SYSTEM(20)  SourceSystem
			String sourceSystemCRTLObj = (String)cluster.getFieldValue("SourceSystem");
			String sourceSystemCRTLObjFmt =  CATFaltFileUtil.getFormattedTxt(sourceSystemCRTLObj,20);
			Log.customer.debug("sourceSystemCRTLObjFmt  ...."+ sourceSystemCRTLObjFmt);

			//SOURCE-FACILITY(8)  SourceFacility
			String sourceFacilityCRTLObj = (String)cluster.getFieldValue("SourceFacility");
			String sourceFacilityCRTLObjFmt =  CATFaltFileUtil.getFormattedTxt(sourceFacilityCRTLObj,8);
			Log.customer.debug("sourceFacilityCRTLObjFmt  ...."+ sourceFacilityCRTLObjFmt);

			//TARGET-SYSTEM(20)  TargetSystem
			String targetSystemCRTLObj = (String)cluster.getFieldValue("TargetSystem");
			String targetSystemCRTLObjFmt =  CATFaltFileUtil.getFormattedTxt(targetSystemCRTLObj,20);
			Log.customer.debug("targetSystemCRTLObjFmt  ...."+ targetSystemCRTLObjFmt);

			//TARGET-FACILITY(8) TargetFacility
			String targetFacilityCRTLObj = (String)cluster.getFieldValue("TargetFacility");
			String targetFacilityCRTLObjFmt =  CATFaltFileUtil.getFormattedTxt(targetFacilityCRTLObj,8);
			Log.customer.debug("targetFacilityCRTLObjFmt  ...."+ targetFacilityCRTLObjFmt);

			//RECORD-COUNT(15)  RecordCount Numeric value of number of records sent on the day. (For CAPS this will always be 1)
			String recordCountCRTLObj = "1";
			String recordCountCRTLObjFmt =  CATFaltFileUtil.getFormattedTxt(recordCountCRTLObj,15);
			Log.customer.debug("recordCountCRTLObjFmt  ...."+ recordCountCRTLObjFmt);

			//USER-AREA-1(45)    TotalAmount Total invoice amount

			// Below code added by VJS to suit reqt - for control file generation
			/*
			BigDecimal totalAmountCRTLObj = (BigDecimal)cluster.getFieldValue("TotalAmount");
			double totalAmountCRTLObjDbl = totalAmountCRTLObj.doubleValue();
			String totalAmountCRTLObjStr = Double.toString(totalAmountCRTLObjDbl);
			String totalAmountCRTLObjFmt =  CATFaltFileUtil.getFormattedTxt(totalAmountCRTLObjStr,45);
			Log.customer.debug("totalAmountCRTLObjFmt  ...."+ totalAmountCRTLObjFmt);
			*/

			BigDecimal totalAmountCRTLObj = (BigDecimal)cluster.getFieldValue("TotalAmount");
			double totalAmountCRTLObjDbl = totalAmountCRTLObj.doubleValue();
			String totalAmountCRTLObjFmt =  CATFaltFileUtil.getFormattedNumber(totalAmountCRTLObjDbl,"0000000000000.00");
			Log.customer.debug("totalAmountCRTLObjFmt before ...."+ totalAmountCRTLObjFmt);
			totalAmountCRTLObjFmt = totalAmountCRTLObjFmt + "                            ";
			Log.customer.debug("totalAmountCRTLObjFmt  ...."+ totalAmountCRTLObjFmt);


			//USER-AREA-2(45)  Area2
			String area2CRTLObj = (String)cluster.getFieldValue("Area2");
			String area2CRTLObjFmt =  CATFaltFileUtil.getFormattedTxt(area2CRTLObj,45);

			//USER-AREA-3(45) Not used
			String area3CRTLObj = " ";
			String area3CRTLObjFmt =  CATFaltFileUtil.getFormattedTxt(area3CRTLObj,45);
			//Base.getSession().transactionCommit();
			ctrlData = uniqueNameCRTLObjFmt+"~|"+CcontrolDateCRTLObjFmt+"~|"+interfaceNameCRTLObjFmt+"~|"+sourceSystemCRTLObjFmt+"~|"+sourceFacilityCRTLObjFmt+"~|"+targetSystemCRTLObjFmt+"~|"+targetFacilityCRTLObjFmt+"~|"+recordCountCRTLObjFmt+"~|"+totalAmountCRTLObjFmt+"~|"+area2CRTLObjFmt+"~|"+area3CRTLObjFmt;
			Log.customer.debug("before return ctrl data ...."+ ctrlData);


		}
		catch(Exception e)
		{
			if (cluster == null)
					Log.customer.debug("Cluster is null after the push....");
			Log.customer.debug(e.toString());
			throw e;
		}
		/*}
		else {
			Log.customer.debug("cluster is NULL  ...."+ cluster);
		}*/
		//Base.getSession().transactionCommit();
		Log.customer.debug("return ctrl data ...."+ ctrlData);
		return ctrlData;


	}

	String getDateTime(Date datetime)
	{
		int yy = (new Integer(Date.getYear(datetime))).intValue();
		int mm = (new Integer(Date.getMonth(datetime))).intValue();
		int dd = (new Integer(Date.getDayOfMonth(datetime))).intValue();
		int hh = (new Integer(Date.getHours(datetime))).intValue();
		int mn = (new Integer(Date.getMinutes(datetime))).intValue();
		int ss = (new Integer(Date.getSeconds(datetime))).intValue();
		mm++;
		String retstr = new String ("");
		retstr = retstr + yy;

		if ( mm/10 == 0)	retstr = retstr + "0" + mm;
		else	retstr = retstr + mm;

		if ( dd/10 == 0)	retstr = retstr + "0" + dd;
		else	retstr = retstr + dd;

		if ( hh/10 == 0)	retstr = retstr + "0" + hh;
		else	retstr = retstr + hh;

		if ( mn/10 == 0)	retstr = retstr + "0" + mn;
		else	retstr = retstr + mn;

		if ( ss/10 == 0)	retstr = retstr + "0" + ss;
		else	retstr = retstr + ss;

		return retstr;
    }



}
