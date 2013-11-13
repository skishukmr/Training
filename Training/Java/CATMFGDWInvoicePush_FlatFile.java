/*******************************************************************************************************************************************

	Creator: Madhavan Chari
	Description: Writing the fileds from the IR to flat file
	ChangeLog:
	Date		Name		History
	--------------------------------------------------------------------------------------------------------------

   Deepak Sharma
   Date : 22/08/2008
   Changing: CAPSUOM
	
   Issue 215
   Vikram Singh
   Date: 22/09/2011
   Change: IhBookdate changed from ControlDate to ApprovedDate

   29/11/2011 IBM AMS Vikram Singh				Filtering non ASCII characters
   15/06/2012 Dharshan   Issue #269	 IsAdHoc - catalog or non catalog,
   10/07/2013 IBM AMS Vikram Singh	 Q4 2013 - RSD117 - FDD 2/TDD 2 - Modify IR uniqueName when sending the IR number to PDW


*******************************************************************************************************************************************/

package config.java.schedule;

import java.io.File;
//change begin by Soumya
import java.io.IOException;
//change end by Soumya
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;

import ariba.util.formatter.BooleanFormatter;
import ariba.base.core.Base;
import ariba.base.core.BaseId;
import ariba.base.core.BaseVector;
import ariba.base.core.ClusterRoot;
import ariba.base.core.Log;
import ariba.base.core.Partition;
import ariba.base.core.aql.AQLOptions;
import ariba.base.core.aql.AQLQuery;
import ariba.base.core.aql.AQLResultCollection;
import ariba.basic.core.CommodityCode;
import ariba.basic.core.Money;
import ariba.basic.core.UnitOfMeasure;
import ariba.common.core.SplitAccounting;
import ariba.common.core.SplitAccountingCollection;
import ariba.invoicing.core.InvoiceReconciliation;
import ariba.invoicing.core.InvoiceReconciliationLineItem;
import ariba.procure.core.LineItemProductDescription;
import ariba.procure.core.ProcureLineType;
import ariba.purchasing.core.DirectOrder;
import ariba.util.core.Date;
import ariba.util.core.FastStringBuffer;
import ariba.util.core.IOUtil;
import ariba.util.core.ResourceService;
import ariba.util.core.StringUtil;
import ariba.util.formatter.BigDecimalFormatter;
import ariba.util.formatter.DateFormatter;
import ariba.util.formatter.IntegerFormatter;
import ariba.util.scheduler.ScheduledTask;
import ariba.util.scheduler.ScheduledTaskException;
import ariba.util.scheduler.Scheduler;
import config.java.common.CatEmailNotificationUtil;
import ariba.approvable.core.LineItem;
//change made by Soumya begins
import config.java.schedule.util.CATFaltFileUtil;
//change made by Soumya ends




//TBD:::Whats needs to be filled if any of the field value is not present. right now writing it with ""
public class CATMFGDWInvoicePush_FlatFile extends ScheduledTask
{
private Partition p;
private String query;
private String controlid, policontrolid;
private int count, lineitemcount;
private double total, lineitemtotal;
private ariba.util.core.Date datetimezone = null;
private BaseId baseId = null;
private boolean isTransferred = false;
private String interfacename = null, tragetfacilityname = null, strarea2, strarea3;
private boolean isHeader = false;
private String classname= "CATMFGDWInvoicePush_FlatFile";
private Calendar calendar = new GregorianCalendar();
private java.util.Date date = calendar.getTime();
private	DateFormat dateFormat = new SimpleDateFormat("MM_dd_yyyy");
private	String fileExt = ""+ dateFormat.format(date);
private String flatFilePath = "/msc/arb9r1/downstream/catdata/DW/MSC_DW_INVOICE_PUSH_UK_"+fileExt+".txt";
//change made by soumya begins

private String archiveFileDataPath = "/msc/arb9r1/downstream/catdata/DW/archive/MSC_DW_INVOICE_PUSH_UK_ARCHIVE"+fileExt+".txt";

//change made by soumya ends
private String periodenddate = null;
private String datasetFacilityCode = "Z1";
private String recordType = "IN";
private String fileLayoutVersion = "8.1.1";
private String periodStartdate= null;
private String partitionName= null;
private String ihRecordStatus = "A";
private String ihBookdate = null;
private BaseVector IrLineItem=null;
private LineItemProductDescription IrLineDescritpiton = null;
private String irLinePoNumber = null;
private int partitionNumber,irLinePoLineNumber;
private String irBuyerCCString = null;
private int totalNumberOfIrs,IrnumberInCollection,irSplitaccSize;
private int totalNumberOfIrWritten = 0;
private FastStringBuffer message = null;
private String mailSubject = null;
private String startTime, endTime;
private String MFGIRFlagAction = null;
private boolean isCompletedFlgUpdate = false;
private ariba.invoicing.core.InvoiceReconciliation invrecon = null;
private String irFacilityFlag = null;
private String irUniqueName = null;
private String irSupplierLocation = null;
private String Irinvdateyymmdd = null;
private Money IrTotalcost = null;
private BigDecimal Irtotalcostamount,irTotalInvoiceDiscountDollarAmount;
private String irTotalCostCurrency = null;
private String irstngTotalCost = null;
private String strgDiscountAmount = null;
private  InvoiceReconciliationLineItem IrLineItem2 = null;
private String numberInCollection = null;
private String irDescItemNumber = null;
private String descDescription = null;
private String descDescription_temp = null;
private String irDescriptionStripped = null;
private CommodityCode irCommodityCode = null;
private String ccUniqueName = null;
private String descSupplierPartNumber = null;
private ProcureLineType IrLineType = null;
private String irHazmat = null;
private String IrLineQuantity = null;
private UnitOfMeasure IrUOM = null;
private String irUOMUniqueName = null;
private String IrdecsPrice = null;
private  String irLineAmount = null;
private ClusterRoot  irLineCapsCharge = null;
private String irCapsUniqueName = null;
private  String irLinePoLinenoString = null;
private SplitAccountingCollection irAccounting = null;
private BaseVector irSplitAccounting = null;
private String IrFacilityUniqueName = null;
private String irFac = null;
private String irFac2 = null;
private String irCCString = null;
private String irStrngsubacc = null;
private String irAccCodeStrng = null;
private SplitAccounting irSplitAccounting2 = null;
private ClusterRoot irFacility = null;
private ClusterRoot irCostCenter = null;
private String irCC = null;
private ClusterRoot irSubAcc = null;
private String irSubAccStrng = null;
private ClusterRoot irAccount = null;
private String irAccCode = null;
private ClusterRoot irBuyerCode = null;
private String irBuyercc = null;
private DirectOrder irLineOrder = null;
private ariba.purchasing.core.POLineItem irOrderLineItem = null;
private ClusterRoot irPOLineBuyerCode = null;
private String irPOlinebuyercc = null;
private Boolean isAdHoc;
private boolean isAdHocBoolean;

/*
 * AUL, sdey 	: Moved the hardcoded values to schedule task parameter.
 * Reason		: Along with 9r Server path might get changed.
 */
	public void init(Scheduler scheduler, String scheduledTaskName, Map arguments) {
		super.init(scheduler, scheduledTaskName, arguments);
		for (Iterator e = arguments.keySet().iterator(); e.hasNext();) {
			String key = (String) e.next();
			if (key.equals("FlatFilePath")) {
				flatFilePath = (String) arguments.get(key);
				flatFilePath = flatFilePath + fileExt + ".txt";
				Log.customer.debug("CATUSDWInvoicePush_FlatFile : FlatFilePath "+ flatFilePath);
			}
		}
	}
/*
 * AUL, sdey 	: Moved the hardcoded values to schedule task parameter.
 * Reason		: Along with 9r Server path might get changed.
 */



public void run()
	throws ScheduledTaskException
{
	Log.customer.debug("%s::Start of CATMFGDWInvoicePush_FlatFile task ...",classname);

	// Read the USPOFlagAction info from cat.dwAction.util
	MFGIRFlagAction = ResourceService.getString("cat.dwAction", "MFGIRFlagAction");
	Log.customer.debug("%s::usPOFlagAction ...", MFGIRFlagAction, classname);
    if (MFGIRFlagAction!=null && ! MFGIRFlagAction.equals("None") && MFGIRFlagAction.indexOf("not found")==-1)
		isCompletedFlgUpdate = false;
	if ( MFGIRFlagAction!=null && MFGIRFlagAction.equals("Completed")&& MFGIRFlagAction.indexOf("not found")==-1)
    	isCompletedFlgUpdate = true;

	startTime = DateFormatter.getStringValue(new ariba.util.core.Date(), "EEE MMM d hh:mm:ss a z yyyy", TimeZone.getTimeZone("CST"));
	periodenddate = DateFormatter.toYearMonthDate(Date.getNow());
	try {
		 File out_FlatFile = new File(flatFilePath);
		 Log.customer.debug("%s::FilePath:%s",classname,out_FlatFile);
		 if (!out_FlatFile.exists()) {
		 	Log.customer.debug("%s::if file does not exit then create 1",classname);
			out_FlatFile.createNewFile();
		 }
		 Log.customer.debug("%s::Creating aprint writer obj:",classname);
		 PrintWriter outPW_FlatFile = new PrintWriter(IOUtil.bufferedOutputStream(out_FlatFile), true);
		 p = Base.getSession().getPartition();
		 message = new FastStringBuffer();
		 mailSubject = "CATMFGDWInvoicePush_FlatFile Task Completion Status - Completed Successfully";
		 try
		 {
		  isHeader = false;
		  query = "select from ariba.invoicing.core.InvoiceReconciliation where DWInvoiceFlag = 'InProcess'";
		  Log.customer.debug(query);
          AQLQuery aqlquery = null;
		  AQLOptions options = null;
		  AQLResultCollection results = null;

          //String topicname = new String("InvoiceReconciliationPush");
		  //String eventsource = new String("ibm_caps_invoicereconpush");

		  aqlquery = AQLQuery.parseQuery(query);
		  options = new AQLOptions(p);
		  results = Base.getService().executeQuery(aqlquery, options);
		  totalNumberOfIrs = results.getSize();
		  if(results.getErrors() != null)
		  	Log.customer.debug("ERROR GETTING RESULTS in Results");
            while(results.next()){
				invrecon = (InvoiceReconciliation)(results.getBaseId("InvoiceReconciliation").get());
				if(invrecon != null){
					try{
                    	IrLineItem = (BaseVector)invrecon.getLineItems();
						Log.customer.debug("%s::Ir Line Item:%s",classname,IrLineItem);
						// Shaila : Issue 754 : To ensure no IR with zero line item is processed
						int lineCount = invrecon.getLineItemsCount();
						Log.customer.debug("%s::Line Item count for IR:%s ",classname,lineCount);
						Log.customer.debug("%s::IR_periodenddate:%s",classname,periodenddate);
						Log.customer.debug("%s::IR_datasetFacilityCode:%s",classname,datasetFacilityCode);
						//outPW_FlatFile.write(periodenddate);
						//outPW_FlatFile.write(datasetFacilityCode);
						partitionNumber = invrecon.getPartitionNumber();
						//String partition_name = String(partitionName);
						Log.customer.debug("%s::IR_Partition:%s",classname,partitionNumber);

						if (partitionNumber==3){
							//Date pSD = (Date)invrecon.getFieldValue("BlockStampDate");
							// Issue 215
							//Date pSD = (Date)invrecon.getFieldValue("ControlDate");
							Date pSD = (Date)invrecon.getFieldValue("ApprovedDate");
							periodStartdate = DateFormatter.toYearMonthDate(pSD);
							Log.customer.debug("%s::Period Start Date:%s",pSD,classname);
							partitionName = "mfg1";
							ihBookdate = DateFormatter.toYearMonthDate(pSD);
							//outPW_FlatFile.write(periodStartdate+"~/");
						 	//outPW_FlatFile.write("U9");
						}
						if (lineCount > 0){
                        	int IrLineItem_vector = IrLineItem.size();
							for (int i =0; i<IrLineItem_vector;i++){
								LineItem irli = (LineItem)IrLineItem.get(i);
								outPW_FlatFile.write(partitionName+"~|");
								irFacilityFlag = (String)invrecon.getFieldValue("FacilityFlag");
								Log.customer.debug("%s::Ir facility flag:%s",classname,irFacilityFlag);
								if (!StringUtil.nullOrEmptyOrBlankString(irFacilityFlag)){
									outPW_FlatFile.write(irFacilityFlag+"~|");
								}
								else{
									outPW_FlatFile.write("~|");
								}
								//outPW_FlatFile.write(recordType+"~|");
								//outPW_FlatFile.write(fileLayoutVersion+"~|"+"M"+"~|");

								irUniqueName = (String)invrecon.getFieldValue("UniqueName");
								Log.customer.debug("%s::irUniqueName: %s",classname,irUniqueName);
								//Start: Q4 2013 - RSD117 - FDD 2/TDD 2
								//outPW_FlatFile.write(irUniqueName+"~|");								
										
								String SupInvNumber = (String)invrecon.getDottedFieldValue("Invoice.InvoiceNumber");
								Log.customer.debug("CATMFGDWInvoicePush_FlatFile: SupInvNumber: "+ SupInvNumber);
								int invleng = SupInvNumber.length();
								if (invleng > 35)
								{	
									String SupInvNumber1 = getFormatattedTxt(SupInvNumber);
									int invleng1 = SupInvNumber1.length();
									Log.customer.debug("CATMFGDWInvoicePush_FlatFile: invleng1: "+ invleng1);
									String FormattedirUniqueName = getFormatattedTxt(irUniqueName, invleng1);
									Log.customer.debug("CATMFGDWInvoicePush_FlatFile: FormattedirUniqueName: "+ FormattedirUniqueName);

									// Writing IH-Invoice-No - 4
									outPW_FlatFile.write(FormattedirUniqueName+"~|");
								}
								else
								{
									String FormattedirUniqueName = getFormatattedTxt(irUniqueName, invleng);
									Log.customer.debug("CATMFGDWInvoicePush_FlatFile: FormattedirUniqueName: "+ FormattedirUniqueName);

									// Writing IH-Invoice-No - 4
									outPW_FlatFile.write(FormattedirUniqueName+"~|");

								}										
								//End: Q4 2013 - RSD117 - FDD 2/TDD 2

								irSupplierLocation = (String)invrecon.getDottedFieldValue("SupplierLocation.UniqueName");
								if (irSupplierLocation != null){
									Log.customer.debug("%s::irSupplierLocation:%s",classname,irSupplierLocation);
									outPW_FlatFile.write(irSupplierLocation+"~|");
								}
								else{
									outPW_FlatFile.write("~|");
								}
								Date Ir_invDate = (Date)invrecon.getFieldValue("InvoiceDate");
								if (Ir_invDate!=null){
									Irinvdateyymmdd = DateFormatter.toYearMonthDate(Ir_invDate);
									Log.customer.debug("%s::Invoice Date:%s",classname,Irinvdateyymmdd);
									outPW_FlatFile.write(Irinvdateyymmdd+"~|");
								}
								else{
									outPW_FlatFile.write("~|");
								}
								//Log.customer.debug("%s::ihRecordStatus:%s",classname,ihRecordStatus);
								//outPW_FlatFile.write(ihRecordStatus+"~|");

								Money IrTotalcost = (Money)invrecon.getFieldValue("TotalCost");
								if (IrTotalcost!=null){
									Irtotalcostamount = (BigDecimal)invrecon.getDottedFieldValue("TotalCost.Amount");
									Log.customer.debug("%s::Total cost amount:%s",classname,Irtotalcostamount);
									if (Irtotalcostamount!=null) {
										int Ir_tca = Irtotalcostamount.intValue();
										if (Ir_tca >= 0) {
											outPW_FlatFile.write("01"+"~|");
										}
										else if (Ir_tca < 0){
											outPW_FlatFile.write("02"+"~|");
										}
										else {
											outPW_FlatFile.write("~|");
										}
										outPW_FlatFile.write(ihBookdate+"~|");
										irTotalCostCurrency = IrTotalcost.getCurrency().getUniqueName();
										if (!StringUtil.nullOrEmptyOrBlankString(irTotalCostCurrency)){
											Log.customer.debug("%s::TotalCost Currency:%s",classname,irTotalCostCurrency);
											outPW_FlatFile.write(irTotalCostCurrency+"~|");
										}
										else {
											outPW_FlatFile.write("~|");
										}
										irstngTotalCost = BigDecimalFormatter.getStringValue(Irtotalcostamount);
										Log.customer.debug("%s::Writing IR total cost:%s",classname,irstngTotalCost);
										outPW_FlatFile.write(irstngTotalCost+"~|");
								    }
								    else {
										outPW_FlatFile.write("~|");
										outPW_FlatFile.write(ihBookdate+"~|");
										outPW_FlatFile.write("~|");
										outPW_FlatFile.write("~|");
								   }
								}
								else {
									outPW_FlatFile.write("~|");
									outPW_FlatFile.write(ihBookdate+"~|");
									outPW_FlatFile.write("~|");
									outPW_FlatFile.write("~|");
								}
                                irTotalInvoiceDiscountDollarAmount = (BigDecimal)invrecon.getDottedFieldValue("TotalInvoiceDiscountDollarAmount.Amount");
								Log.customer.debug("%s::TotalInvoiceDiscountDollarAmount:%s",classname,irTotalInvoiceDiscountDollarAmount);
								/* if (irTotalInvoiceDiscountDollarAmount==Constants.ZeroBigDecimal) {
									//Log.customer.debug("%s::TotalInvoiceDiscountDollarAmount is:%s",classname,TotalInvoiceDiscountDollarAmount_2);
									 outPW_FlatFile.write("0000000000000.00"+"\n");
									}*/
									if (irTotalInvoiceDiscountDollarAmount!=null) {
										int  ir_TIDDA = irTotalInvoiceDiscountDollarAmount.intValue();
										Log.customer.debug("%s::TotalInvoiceDiscountDollarAmount integer value:%s",classname,ir_TIDDA);
										if (ir_TIDDA==0){
											outPW_FlatFile.write("0000000000000.00"+"~|");
										}
										else {
											strgDiscountAmount = BigDecimalFormatter.getStringValue(irTotalInvoiceDiscountDollarAmount);
											Log.customer.debug("%s::Writting the discount amount:%s",classname,strgDiscountAmount);
											outPW_FlatFile.write(strgDiscountAmount+"~|");
										}
                                     }
                                     else {
									 	Log.customer.debug("%s::No value for irTotalInvoiceDiscountDollarAmount so leaving it blanck");
										outPW_FlatFile.write("~|");
									 }
									 IrLineItem2 = (InvoiceReconciliationLineItem)IrLineItem.get(i);
									 IrnumberInCollection = IrLineItem2.getNumberInCollection();
									 numberInCollection = IntegerFormatter.getStringValue(IrnumberInCollection);
									 Log.customer.debug("%s::IR number in collection:%s",classname,numberInCollection);
									 outPW_FlatFile.write(numberInCollection+"~|");

									 IrLineDescritpiton = IrLineItem2.getDescription();
									 if (IrLineDescritpiton!=null){
									 	Log.customer.debug("%s::IR_LineDescription:%s",classname,IrLineDescritpiton);
										irDescItemNumber = IrLineDescritpiton.getItemNumber(); //what if this is blanck, right now considering blanck also as null
										Log.customer.debug("%s::Writing IR_LineDescription Item Number:%s",classname,irDescItemNumber);
										if (!StringUtil.nullOrEmptyOrBlankString(irDescItemNumber)){
											outPW_FlatFile.write(irDescItemNumber+"~|");
											outPW_FlatFile.write("K"+"~|");
										}
										else {
											outPW_FlatFile.write("~|");
											outPW_FlatFile.write("0"+"~|");
										}
										// Filtering Non-ASCII characters
										String descDescription = "";
										descDescription_temp = (String)IrLineDescritpiton.getFieldValue("Description");
										descDescription = descDescription_temp.replaceAll("[^\\p{ASCII}]", "");
										if (!StringUtil.nullOrEmptyOrBlankString(descDescription)){
											descDescription = StringUtil.replaceCharByChar(descDescription,'\r',' ');
											descDescription = StringUtil.replaceCharByChar(descDescription,'\t',' ');
											descDescription = StringUtil.replaceCharByChar(descDescription,'\n',' ');
											Log.customer.debug("%s::Description:%s",classname,descDescription);
											outPW_FlatFile.write(descDescription+"~|");
										}
										else{
											outPW_FlatFile.write("~|");
										}
										irCommodityCode = IrLineDescritpiton.getCommonCommodityCode();
										Log.customer.debug("%s::IR Description CommodityCode:%s",classname,irCommodityCode);
										if (irCommodityCode!=null){
											ccUniqueName = irCommodityCode.getUniqueName();
											Log.customer.debug("%s::IR Description CommodityCode UniqueName:%s",classname,ccUniqueName);
											outPW_FlatFile.write(ccUniqueName+"~|");
										}
										else {
											outPW_FlatFile.write("~|");
										}
										descSupplierPartNumber = IrLineDescritpiton.getSupplierPartNumber();
										Log.customer.debug("%s::Description_SupplierPartNumber:%s",classname,descSupplierPartNumber);
										if(!StringUtil.nullOrEmptyOrBlankString(descSupplierPartNumber)){
											Log.customer.debug("%s::Writing Description_SupplierPartNumber:%s",classname,descSupplierPartNumber);
											outPW_FlatFile.write(descSupplierPartNumber+"~|");
										}
										else {
											outPW_FlatFile.write("~|");
										}
									}
									else {
										outPW_FlatFile.write("~|");
										outPW_FlatFile.write("~|");
										outPW_FlatFile.write("~|");
										outPW_FlatFile.write("~|");
										outPW_FlatFile.write("~|");
									}
									IrLineType = IrLineItem2.getLineType();
									if(IrLineType!=null){
										int category = IrLineType.getCategory();
										if (category==1){
											outPW_FlatFile.write("01"+"~|");
									    }
										else if (category==2){
											String irLineTypeString = (String)IrLineType.getFieldValue("UniqueName");
											Log.customer.debug("%s:: LineType of Ir Line::%s",classname,irLineTypeString);
											if(irLineTypeString.equals("VATCharge")){
												outPW_FlatFile.write("96"+"~|");
											}
											else {
												outPW_FlatFile.write("02"+"~|");
											}
										}
										else if (category==4){
											outPW_FlatFile.write("05"+"~|");
										}
										else if (category==8) {
											outPW_FlatFile.write("27"+"~|");
										}
										else if (category==16){
											outPW_FlatFile.write("13"+"~|");
										}
										else if (category==32) {
											outPW_FlatFile.write("12"+"~|");
										}
									}
									else {
										outPW_FlatFile.write("~|");
									}


									Boolean isHaz = (Boolean)IrLineItem2.getFieldValue("IsHazmat");
									// irHazmat = BooleanFormatter.getStringValue(IrLineItem2.getFieldValue("IsHazmat"));
									if(isHaz != null)
									{
									 boolean irHazmatBoolean = isHaz.booleanValue();
									// Log.customer.debug("Ir Hazmat value:",irHazmatBoolean);
									 if(irHazmatBoolean)
									 {
										outPW_FlatFile.write("3"+"~|");
										}
										else {
											outPW_FlatFile.write("0"+"~|");
										}
									 }
									 else {
										outPW_FlatFile.write("0~|");
											 }

									/*
									irHazmat = BooleanFormatter.getStringValue(IrLineItem2.getFieldValue("IsHazmat"));
									Log.customer.debug("%s::Ir Hazmat value:%s",classname,irHazmat);
									if (!StringUtil.nullOrEmptyOrBlankString(irHazmat)){
										if (irHazmat=="true"){
											outPW_FlatFile.write("3"+"~|");
										}
										else {
											outPW_FlatFile.write("0"+"~|");
										}
									}
									else {
										outPW_FlatFile.write("0~|");
									}
									*/

									IrLineQuantity = BigDecimalFormatter.getStringValue(IrLineItem2.getQuantity());
									if (!StringUtil.nullOrEmptyOrBlankString(IrLineQuantity)){
											Log.customer.debug("%s::IR LineQuantity:%s",classname,IrLineQuantity);
											outPW_FlatFile.write(IrLineQuantity+"~|");
									}
									else {
										outPW_FlatFile.write("~|");
									}


									//IrUOM = (UnitOfMeasure)IrLineDescritpiton.getUnitOfMeasure();
									String irUOM_UniqueName = null;
									if (IrLineItem2.getDottedFieldValue("Description.UnitOfMeasure") !=null){
										String uOMUniqueName = (String)IrLineItem2.getDottedFieldValue("Description.UnitOfMeasure.UniqueName");
										//irUOMUniqueName = 	IrUOM.getUniqueName();
										// irUOMUniqueName = 	IrUOM.getFieldValue("CAPSUnitOfMeasure").toString();
										// irUOMUniqueName = 	IrUOM.getFieldValue("CAPSUnitOfMeasure");
										//Object irUOM_object = 	IrUOM.getFieldValue("CAPSUnitOfMeasure");
										Object irUOM_object = 	IrLineItem2.getDottedFieldValue("Description.UnitOfMeasure.CAPSUnitOfMeasure");
										 if(irUOM_object != null)	{
							                  irUOM_UniqueName = irUOM_object.toString();
										Log.customer.debug("%s::IR Desc UOM:%s",classname,irUOM_UniqueName);
										if (!StringUtil.nullOrEmptyOrBlankString(irUOM_UniqueName)){
											outPW_FlatFile.write(irUOM_UniqueName+"~|");
										}
										else {
											//	 IF CAPSUnitOfMeasure = Empty  THEN LineItems.Description.UnitOfMeasure.UniqueName
											if (!StringUtil.nullOrEmptyOrBlankString(uOMUniqueName))
											outPW_FlatFile.write(uOMUniqueName+"~|");
										}
									}

										else {
											//outPW_FlatFile.write("~|");
											// IF CAPSUnitOfMeasure = NULL  THEN LineItems.Description.UnitOfMeasure.UniqueName
											if (!StringUtil.nullOrEmptyOrBlankString(uOMUniqueName))
											outPW_FlatFile.write(uOMUniqueName+"~|");
										}
									}

									else {
										outPW_FlatFile.write("~|");
									}
									if (IrTotalcost!=null){
										irTotalCostCurrency = IrTotalcost.getCurrency().getUniqueName();
										if (!StringUtil.nullOrEmptyOrBlankString(irTotalCostCurrency)){
											Log.customer.debug("%s::TotalCost Currency:%s",classname,irTotalCostCurrency);
											outPW_FlatFile.write(irTotalCostCurrency+"~|");
										}
										else {
											outPW_FlatFile.write("~|");
										}
									 }
									 else {
									 	outPW_FlatFile.write("~|");
									 }
									 IrdecsPrice =  BigDecimalFormatter.getStringValue(IrLineDescritpiton.getPrice().getAmount());
									 Log.customer.debug("%s::Ir desc Price:%s",classname,IrdecsPrice);
									 if (!StringUtil.nullOrEmptyOrBlankString(IrdecsPrice)){
									 	outPW_FlatFile.write(IrdecsPrice+"~|");
									 }
									 else {
									 	outPW_FlatFile.write("~|");
									 }
									 String irUOM_UniqueName2 = null;
									// IrUOM = (UnitOfMeasure)IrLineDescritpiton.getUnitOfMeasure();
									 if (IrLineItem2.getDottedFieldValue("Description.UnitOfMeasure") != null){
										 String uOMUniqueNameLi = (String)IrLineItem2.getDottedFieldValue("Description.UnitOfMeasure.UniqueName");
									 	//irUOMUniqueName = 	IrUOM.getUniqueName();
										 //String irUOMUniqueName = 	IrUOM.getFieldValue("CAPSUnitOfMeasure").toString();
										 Object irUOM_object = 	IrLineItem2.getDottedFieldValue("Description.UnitOfMeasure.CAPSUnitOfMeasure");
										 if(irUOM_object != null) {
							                  irUOM_UniqueName2 = irUOM_object.toString();
										Log.customer.debug("%s::IR Desc UOM:%s",classname,irUOM_UniqueName2);
										if (!StringUtil.nullOrEmptyOrBlankString(irUOM_UniqueName2)){
											outPW_FlatFile.write(irUOM_UniqueName2+"~|");
										 }
										 else {
											 //	 IF CAPSUnitOfMeasure = NULL  THEN LineItems.Description.UnitOfMeasure.UniqueName
											 if (!StringUtil.nullOrEmptyOrBlankString(uOMUniqueNameLi))
												outPW_FlatFile.write(uOMUniqueNameLi+"~|");
										}
									 }
										 else {
										 	//outPW_FlatFile.write("~|");
										 	// IF CAPSUnitOfMeasure = NULL  THEN LineItems.Description.UnitOfMeasure.UniqueName
											 if (!StringUtil.nullOrEmptyOrBlankString(uOMUniqueNameLi))
												outPW_FlatFile.write(uOMUniqueNameLi+"~|");

										 }
									 }
									 else {
									 	outPW_FlatFile.write("~|");
									 }
									 outPW_FlatFile.write("1.0"+"~|");
									 irLineAmount = BigDecimalFormatter.getStringValue(IrLineItem2.getAmount().getAmount());
									 if (!StringUtil.nullOrEmptyOrBlankString(irLineAmount)) {
									 	Log.customer.debug("%s::Ir Line Amount:%s",classname,irLineAmount);
										outPW_FlatFile.write(irLineAmount+"~|");
									 }
									 else {
									 	outPW_FlatFile.write("~|");
									 }
									 outPW_FlatFile.write("00000.0000"+"~|");
									 irLineCapsCharge = (ClusterRoot)IrLineItem2.getFieldValue("CapsChargeCode");
									 if (irLineCapsCharge!=null){
									 	Log.customer.debug("%s::Ir Line caps charge code:%s",classname,irLineCapsCharge);
										irCapsUniqueName = (String)irLineCapsCharge.getFieldValue("UniqueName");
										Log.customer.debug("%s::Ir Line caps charge code UniqueName:%s",classname,irCapsUniqueName);
										outPW_FlatFile.write(irCapsUniqueName+"~|");
									 }
									 else {
									 	outPW_FlatFile.write("~|");
									 }
									 irLinePoNumber = (String)invrecon.getFieldValue("PONumber");
									 if (!StringUtil.nullOrEmptyOrBlankString(irLinePoNumber)){
									 	Log.customer.debug("%s::IR Line PO Number:%s",classname,irLinePoNumber);
									  	outPW_FlatFile.write(irLinePoNumber+"~|");
									 }
									 else {
									 	outPW_FlatFile.write("~|");
									 }
									 irLinePoLineNumber = IntegerFormatter.getIntValue(IrLineItem2.getFieldValue("POLineItemNumber"));
									 irLinePoLinenoString = IntegerFormatter.getStringValue(irLinePoLineNumber);
									 Log.customer.debug("%s::Line po Line number:%s",classname,irLinePoLineNumber);
									 if (!StringUtil.nullOrEmptyOrBlankString(irLinePoLinenoString) && !irLinePoLinenoString.equals("0")){

									 		Log.customer.debug("%s::Ir Line Po Line Number:%s",classname,irLinePoLinenoString);
									 		//String irLinePoLinenoString = IntegerFormatter.getStringValue(irLinePoLineNumber);
											outPW_FlatFile.write(irLinePoLinenoString+"~|");
									 	}
									 	else {
									 		// IF IR.LineItems. POLineItemNumber is NULL THEN SEND VALUE OF 1
											outPW_FlatFile.write("1"+"~|");
									 }
									 if (irLinePoNumber!=null){
									 	if (StringUtil.startsWithIgnoreCase(irLinePoNumber , "C")){
											outPW_FlatFile.write("C"+"~|");
										}
										else{
											outPW_FlatFile.write("P"+"~|");
										}
									 }
									 else {
									 	outPW_FlatFile.write("N"+"~|");
									 }
									 int partitionNumber_2 = invrecon.getPartitionNumber();
									 Log.customer.debug("%s::partiton number 2nd place:%s",classname,partitionNumber_2);
									 if (partitionNumber_2==2){
									 	irAccounting = IrLineItem2.getAccountings();
										if (irAccounting!=null){
											irSplitAccounting = (BaseVector)irAccounting.getSplitAccountings();
											irSplitaccSize = irSplitAccounting.size();
											Log.customer.debug("%s::Split acc size:%s",classname,irSplitaccSize);
											if (irSplitaccSize > 0){
												Log.customer.debug ("%s::getting accounting facility",classname);
												String ir_AccFac= null;
												String ir_Order_name = null;
												for (int j = 0; j<irSplitaccSize;j++){
													irSplitAccounting2 = (SplitAccounting)irSplitAccounting.get(0);
													//String irAccountingFacility = (String)irSplitAccounting2.getFieldValue("AccountingFacility");
													String irAccountingFacility = (String)irSplitAccounting2.getFieldValue("Facility.UniqueName");
													Log.customer.debug("%s::Accounting facility:%s",classname,irAccountingFacility);
													String ir_Order = (String)irSplitAccounting2.getFieldValue("Order");
													if(!StringUtil.nullOrEmptyOrBlankString(irAccountingFacility)){
														Log.customer.debug("%s::Accounting facility:%s",classname,irAccountingFacility);
														ir_AccFac = irAccountingFacility+"~|";
														//outPW_FlatFile.write(irAccountingFacility+"~|");
													}
													else {
														ir_AccFac = ("AA"+"~|");
														// outPW_FlatFile.write("~|"+"\n");
													}
													if (!StringUtil.nullOrEmptyOrBlankString(ir_Order)){
														Log.customer.debug("%s::Accounting Order:%s",classname,ir_Order);
														ir_Order_name = ir_Order;
													}
													else {
														ir_Order_name =("~|");
                                                    }
												}
												outPW_FlatFile.write(ir_AccFac);
												outPW_FlatFile.write(ir_Order_name);
                                              }
											  else{
											  	outPW_FlatFile.write("~|");
												//outPW_FlatFile.write("~|"+"\n");
											  	outPW_FlatFile.write("~|");
											  }
										 }
										 else {
											outPW_FlatFile.write("~|");
											outPW_FlatFile.write("~|");
										}
									  }
									  else {
									  	irAccounting = IrLineItem2.getAccountings();
										if (irAccounting!=null){
											irSplitAccounting = (BaseVector)irAccounting.getSplitAccountings();
											irSplitaccSize = irSplitAccounting.size();
											if (irSplitaccSize > 0){

												for (int k = 0; k<irSplitaccSize;k++){
													irSplitAccounting2 = (SplitAccounting)irSplitAccounting.get(0);
													irFacility = (ClusterRoot)irSplitAccounting2.getFieldValue("Facility");
													if (irFacility!=null){
														IrFacilityUniqueName = (String)irFacility.getFieldValue("UniqueName");
														if(!StringUtil.nullOrEmptyOrBlankString(IrFacilityUniqueName)){
															Log.customer.debug("%s:: IR Facility:%s",classname,IrFacilityUniqueName);
															irFac = IrFacilityUniqueName+"~|";
															irFac2 = IrFacilityUniqueName+"~|";
															//outPW_FlatFile.write(IrFacilityUniqueName+"\n");
														}
														else {
															irFac = ("~|");
															irFac2 = ("AA"+"~|");
															//outPW_FlatFile.write("~|"+"\n");
														}
													}
													else {
														irFac = ("~|");
														irFac2 = ("AA"+"~|");
													}
													irCostCenter = (ClusterRoot)irSplitAccounting2.getFieldValue("CostCenter");
													if (irCostCenter!=null){
														irCC = (String)irCostCenter.getFieldValue("CostCenterCode");
														if(!StringUtil.nullOrEmptyOrBlankString(irCC)){
															Log.customer.debug("%s:: IR Cost Center:%s",classname,irCC);
															irCCString = irCC+"~|";
														}
														else {
															irCCString = ("~|");
														}
													}
													else {
														irCCString = ("~|");
													}
													irSubAcc = (ClusterRoot)irSplitAccounting2.getFieldValue("SubAccount");
													if (irSubAcc!=null){
														irSubAccStrng = (String)irSubAcc.getFieldValue("UniqueName");
														if(!StringUtil.nullOrEmptyOrBlankString(irSubAccStrng)){
															Log.customer.debug("%s:: IR Sub Account:%s",classname,irSubAccStrng);
															irStrngsubacc = irSubAccStrng+"~|";
														}
														else {
															irStrngsubacc = ("~|");
														}
													}
													else {
														irStrngsubacc = ("~|");
													}
													irAccount = (ClusterRoot)irSplitAccounting2.getFieldValue("Account");
													if(irAccount!=null){
														irAccCode = (String)irAccount.getFieldValue("AccountCode");
														if(!StringUtil.nullOrEmptyOrBlankString(irAccCode)){
															Log.customer.debug("%s:: IR Account code:%s",classname,irAccCode);
															irAccCodeStrng = irAccCode+"~|";
														}
														else {
															irAccCodeStrng = ("~|");
														}
                                                     }
													 else {
													 	irAccCodeStrng = ("~|");
													 }
                                                   }
													outPW_FlatFile.write(irFac);
													outPW_FlatFile.write(irFac2);
													outPW_FlatFile.write(irFac);
													outPW_FlatFile.write(irCCString);
													outPW_FlatFile.write(irStrngsubacc);
													outPW_FlatFile.write("~|");
													outPW_FlatFile.write(irAccCodeStrng);
													outPW_FlatFile.write("~|");
													outPW_FlatFile.write("~|");
													outPW_FlatFile.write(irFac);
                                               }
										   }
											irBuyerCode = (ClusterRoot)IrLineItem2.getFieldValue("BuyerCode");
											if (irBuyerCode!=null){
												irBuyercc = (String)irBuyerCode.getFieldValue("BuyerCode");
												if(!StringUtil.nullOrEmptyOrBlankString(irBuyercc)){
													Log.customer.debug("%s:: IR Buyer Code:%s",classname,irBuyercc);
													outPW_FlatFile.write(irBuyercc);
													//outPW_FlatFile.write("\n");
												}
												else {
													//outPW_FlatFile.write("~|");
													//outPW_FlatFile.write("\n");
												}
											}
											else {
												irLineOrder = (DirectOrder)IrLineItem2.getOrder();
												Log.customer.debug("%s::IR line Purchase Order:%s",classname,irLineOrder);
												if (irLineOrder!=null){
													irOrderLineItem = (ariba.purchasing.core.POLineItem)IrLineItem2.getOrderLineItem();
													Log.customer.debug("%s::Order Line Item:%s",classname,irOrderLineItem);
													if (irOrderLineItem!=null){
														irPOLineBuyerCode = (ClusterRoot)irOrderLineItem.getFieldValue("BuyerCode");
														if (irPOLineBuyerCode!= null){
															irPOlinebuyercc = (String)irPOLineBuyerCode.getFieldValue("BuyerCode");
															Log.customer.debug("%s::Ir PO Line Buyer Code");
															outPW_FlatFile.write(irPOlinebuyercc +"~|");
															//outPW_FlatFile.write("\n");
														}
														else{
															//outPW_FlatFile.write("~|");
															//outPW_FlatFile.write("\n");
														}

												    }
													else{
														//outPW_FlatFile.write("~|");
														//outPW_FlatFile.write("\n");
													}
											    }
												else{
													//outPW_FlatFile.write("~|");
													//outPW_FlatFile.write("\n");
												}
											}
										}
									//IsAdHoc - catalog or non catalog, Issue #269 - Dharshan
										isAdHocBoolean = true;
										isAdHoc = null;
										if (irli.getDottedFieldValue("IsAdHoc") != null) {
											isAdHoc = (Boolean) irli.getDottedFieldValue("IsAdHoc");
											isAdHocBoolean = BooleanFormatter.getBooleanValue(isAdHoc);
											Log.customer.debug("%s::isAdHocBoolean:%s",classname,isAdHocBoolean);
											if(isAdHocBoolean == false){
												outPW_FlatFile.write("~|Catalog Item:");
											}
											else {Log.customer.debug("%s::isAdHocBoolean is true, not catalog item",classname);
											outPW_FlatFile.write("~|");
											}
										}
										else {Log.customer.debug("%s::isAdHocBoolean is null, leave blank",classname);
										outPW_FlatFile.write("~|");
										}

										outPW_FlatFile.write("\n");
									 }
									 totalNumberOfIrWritten++;
									 if(isCompletedFlgUpdate) {
									 	Log.customer.debug("%s::MFGIRFlagAction is Completed setting DWInvoiceFlag ...", classname);
									 	invrecon.setFieldValue("DWInvoiceFlag", "Completed");
									 }
									 else {
									 	Log.customer.debug("%s::MFGIRFlagAction is None no action req DWInvoiceFlag ...", classname);
									 	continue;
									}
								}

							 }
							 catch(Exception e){
							 	Log.customer.debug(e.toString());
							 	new ScheduledTaskException("Error : " + e.toString(), e);
                                throw new ScheduledTaskException("Error : " + e.toString(), e);

							 }
							 //Base.getSession().transactionCommit();
							 Log.customer.debug("Ending DWInvoicePush program .....");
							 //invrecon.setFieldValue("DWInvoiceFlag", "Completed");
							 //Update DWPOFlag in DO based on config

					  }
				 }
            }
			catch(Exception e){
				Log.customer.debug(e.toString());
				new ScheduledTaskException("Error : " + e.toString(), e);
                throw new ScheduledTaskException("Error : " + e.toString(), e);

			}
			if (outPW_FlatFile!=null){
				outPW_FlatFile.flush();
				outPW_FlatFile.close();
			}
			endTime = DateFormatter.getStringValue(new Date(), "EEE MMM d hh:mm:ss a z yyyy", TimeZone.getTimeZone("CST"));
            //Process runDWFTP = Runtime.getRuntime().exec("/usr/bin/sh /msc/arb821/Server/config/java/schedule/DWFileFTP_UK.sh");
		}
		catch(Exception e){
			Log.customer.debug(e.toString());
			message.append("Task start time : "+ startTime);
			message.append("\n");
			message.append("Task end time : " + endTime);
			message.append("\n");
			message.append("No of IR Written for DW : "+ totalNumberOfIrWritten);
			message.append("\n");
			message.append("No of IR could not be Written  :"+ (totalNumberOfIrs - totalNumberOfIrWritten));
			message.append("\n");
			message.append("CATMFGDWInvoicePush_FlatFile Task Failed - Exception details below");
			message.append("\n");
			message.append(e.toString());
			mailSubject = "CATMFGDWInvoicePush_FlatFile Task Failed";
			Log.customer.debug("%s: Inside Exception message "+ message.toString() , classname);
			new ScheduledTaskException("Error : " + e.toString(), e);
            throw new ScheduledTaskException("Error : " + e.toString(), e);
		}
		finally {

				//Change made by Soumya begins
				Log.customer.debug("%s::Archive File Path:%s",classname,archiveFileDataPath);
				Log.customer.debug("CATMFGDWInvoicePush_FlatFile:Starting Copying the flat file to Archive ");
				CATFaltFileUtil.copyFile(flatFilePath, archiveFileDataPath);
				Log.customer.debug("CATMFGDWInvoicePush_FlatFile:Completed Copying the flat file to Archive ");

				try
				{
					Log.customer.debug("CATMFGDWInvoicePush_FlatFile:Changing file permission of Data file.");
					Runtime.getRuntime().exec("chmod 666 " + flatFilePath);
					Log.customer.debug("CATMFGDWInvoicePush_FlatFile:Changed file permission of Data file.");
				}catch (IOException e1) {
					Log.customer.debug("CATMFGDWInvoicePush_FlatFile:Error in changing Permission. "+ e1);
				}

				//Change made by Soumya end


			Log.customer.debug("%s: Inside Finally ", classname);
			message.append("Task start time : "+ startTime);
			Log.customer.debug("%s: Inside Finally added start time", classname);
			message.append("\n");
			message.append("Task end time : " + endTime);
			message.append("\n");
			message.append("Total number of IRs to be Written : "+ totalNumberOfIrs);
			message.append("\n");
			message.append("Total number of IRs Written : "+ totalNumberOfIrWritten);
			message.append("\n");
			Log.customer.debug("%s: Inside Finally message "+ message.toString() , classname);

			// Sending email
			CatEmailNotificationUtil.sendEmailNotification(mailSubject, message.toString(), "cat.java.emails", "DWPushNotify");
			message = null;
			totalNumberOfIrWritten =0;
			totalNumberOfIrs =0;
	   }
}

	//Start: Q4 2013 - RSD117 - FDD 2/TDD 2
	private String getFormatattedTxt(String inputTxt, int txtLength) {
		int fulllength = inputTxt.length(); // full length gives the length of IR number
		int rellength = txtLength + 4; //Inv Number + 'IRX' + '-'
		int temp = fulllength - rellength;
		String formattedTxt = "";

		Log.customer.debug("CATMFGDWInvoicePush_FlatFile: int temp  " + temp);

		inputTxt = inputTxt.substring(rellength, fulllength);


		formattedTxt = "IRX-" + inputTxt;
		Log.customer.debug("CATMFGDWInvoicePush_FlatFile: formattedTxt " + formattedTxt);
		return formattedTxt;

		}

	private String getFormatattedTxt(String inputTxt2) {

		inputTxt2 = inputTxt2.substring(0,35);
		Log.customer.debug("CATMFGDWInvoicePush_FlatFile: inputTxt2 " + inputTxt2);
		return inputTxt2;

		}
	//End: Q4 2013 - RSD117 - FDD 2/TDD 2

public CATMFGDWInvoicePush_FlatFile(){

}



}