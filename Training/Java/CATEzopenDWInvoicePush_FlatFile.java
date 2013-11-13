/*******************************************************************************************************************************************

	Creator: Madhavan Chari
	Description: Writing the feilds from the IR to flat file
	ChangeLog:
	Date		Name		History
	--------------------------------------------------------------------------------------------------------------

05/16/08     Kannan        Following fileds has been updated:
                           Source-Of-Record Facility  ( New)
                           IH-Book-Date to  ControlDate
                           IL-Charge-Type is applicable for Ezopen
                           IL-Buy-Facility-Code to LineItems. Accountings. SplitAccountings. Facility .UniqueName
                           IL-Recv-Facility-Code Duplicate Fileds

   Deepak Sharma
    Date : 22/08/2008
   Changing: CAPSUOM

   29/11/2011 IBM AMS Vikram Singh				Filtering non ASCII characters
   15/06/2012 Dharshan   Issue #269	 IsAdHoc - catalog or non catalog, 
   10/07/2013 IBM AMS Vikram Singh		Q4 2013 - RSD117 - FDD 2/TDD 2 - Modify IR uniqueName when sending the IR number to PDW


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

import ariba.base.core.Base;
import ariba.base.core.BaseId;
import ariba.base.core.BaseObject;
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
import ariba.util.core.Fmt;
import ariba.util.core.IOUtil;
import ariba.util.core.StringUtil;
import ariba.util.formatter.BigDecimalFormatter;
import ariba.util.formatter.DateFormatter;
import ariba.util.formatter.IntegerFormatter;
import ariba.util.scheduler.ScheduledTask;
import ariba.util.scheduler.ScheduledTaskException;
import ariba.util.scheduler.Scheduler;
import config.java.common.CatEmailNotificationUtil;
import ariba.util.core.ResourceService;
import ariba.util.formatter.BooleanFormatter;
import ariba.approvable.core.LineItem;
//change made by Soumya begins
import config.java.schedule.util.CATFaltFileUtil;
//change made by Soumya ends





//TBD:::Whats needs to be filled if any of the field value is not present. right now writing it with ""
public class CATEzopenDWInvoicePush_FlatFile extends ScheduledTask
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
    private String classname="CATEzopenDWInvoicePush_FlatFile";
    private Calendar calendar = new GregorianCalendar();
	private	java.util.Date date = calendar.getTime();
	private	DateFormat dateFormat = new SimpleDateFormat("MM_dd_yyyy");
	private	String fileExt = ""+ dateFormat.format(date);
    private String flatFilePath = "/msc/arb9r1/downstream/catdata/DW/MSC_DW_INVOICE_PUSH_Ezopen_"+fileExt+".txt";
    //change made by soumya begins

    private String archiveFileDataPath = "/msc/arb9r1/downstream/catdata/DW/archive/MSC_DW_INVOICE_PUSH_Ezopen_ARCHIVE"+fileExt+".txt";

    //change made by soumya ends
    private String periodEndDate = null;
    private String datasetFacilityCode = "Z1";
    private String recordType = "IN";
    private String fileLayoutVersion = "8.1.1";
    private String periodStartdate = null;
    private String partitionName= null;
    private String ihRecordStatus = "A";
    private String ihBookdate = null;
    private BaseVector irLineItem=null;
    private LineItemProductDescription irLineDescritpiton = null;
    private String irLinePoNumber = null;
    private int partitionNumber;
    private int totalNumberOfIrs;
    private int totalNumberOfIrWritten = 0;
    private FastStringBuffer message = null;
	private String mailSubject = null;
	private String startTime, endTime;
	private String EZOPENIRFlagAction = null;
	private boolean isCompletedFlgUpdate = false;
	//private String ir_SourceOfRecord = null;
	private int lineCount,irLineItemVector;
	private String sourceOfRecord = null;
	private String irUniqueName = null;
	private String irSupplierLocation = null;
	private String irInvdateyymmdd = null;
	private Money irTotalcost = null;
	private BigDecimal irTotalCostAmount,irTotalInvoiceDiscountDollarAmount;
	private String irTotalCostCurrency = null;
	private String irStngTotalCost = null;
	private int  irTIDDA,irNumberInCollection,category,irLinePoLineNumber,partitionNumber2,irSplitaccSize;
	private String numberInCollection = null;
	private String irDescItemNumber = null;
	private String descDescription = null;
	private String descDescription_temp = null;
	private CommodityCode irCommodityCode = null;
	private String ccUniqueName = null;
	private String descSupplierPartNumber = null;
	private ProcureLineType irLineType = null;
	private String irHazmat = null;
	private String irLineQuantity = null;
	private UnitOfMeasure irUOM = null;
	private String irdecsPrice  = null;
	private String irLineAmount = null;
	private ClusterRoot  irLineCapsCharge = null;
	private String irLinePoLinenoString = null;
	private SplitAccountingCollection irAccounting = null;
	private BaseVector irSplitAccounting = null;
	private String irAccFac= null;
	private String irAccRecFac= null;
	private String irOrderName = null;
	private String irDivisionString = null;
	private String irSectionString = null;
	private String irExpenseAccountString = null;
	private String irMiscString = null;
	private String ilRecvFacilityCode = null;
	private String irOrder = null;
	private String irDivision = null;
	private String irSection = null;
	private String irExpenseAccount = null;
	private String irMisc = null;
	private ClusterRoot irBuyerCode = null;
	private String ir_buyercc = null;
	private DirectOrder irLineOrder = null;
	private ariba.purchasing.core.POLineItem irorderLineItem = null;
	private ClusterRoot irPOLineBuyerCode = null;
	private String irPOlinebuyercc = null;
	private String irFacilityUniqueName  = null;
	private String irFac = null;
	private SplitAccounting irSplitAccounting2  = null;
	private ClusterRoot   irFacility  = null;
	private String irDepartment = null;
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
	Log.customer.debug("%s::Start of CATEzopenDWInvoicePush_FlatFile ...",classname);
	//Read the EZOPENIRFlagAction info from cat.dwAction.util
	        EZOPENIRFlagAction = Fmt.Sil("cat.dwAction", "EZOPENIRFlagAction");
	        Log.customer.debug("%s::usPOFlagAction ...", EZOPENIRFlagAction, classname);
	        if (EZOPENIRFlagAction!=null && ! EZOPENIRFlagAction.equals("None") && EZOPENIRFlagAction.indexOf("not found")==-1)
	        	isCompletedFlgUpdate = false;
	        if ( EZOPENIRFlagAction!=null && EZOPENIRFlagAction.equals("Completed")&& EZOPENIRFlagAction.indexOf("not found")==-1)
        		isCompletedFlgUpdate = true;

	startTime = DateFormatter.getStringValue(new ariba.util.core.Date(), "EEE MMM d hh:mm:ss a z yyyy", TimeZone.getTimeZone("CST"));
	periodEndDate = DateFormatter.toYearMonthDate(Date.getNow());
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
				mailSubject = "CATEzopenDWInvoicePush_FlatFile Task Completion Status - Completed Successfully";
				try
				{
					isHeader = false;
					query = "select from ariba.invoicing.core.InvoiceReconciliation where DWInvoiceFlag = 'InProcess'";
					Log.customer.debug(query);

					AQLQuery aqlquery = null;
					AQLOptions options = null;
					AQLResultCollection results = null;
					ariba.invoicing.core.InvoiceReconciliation invrecon = null;

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

								irLineItem = (BaseVector)invrecon.getLineItems();
								Log.customer.debug("%s::Ir Line Item:%s",classname,irLineItem);
								// Shaila : Issue 754 : To ensure no IR with zero line item is processed
								lineCount = invrecon.getLineItemsCount();
								Log.customer.debug("%s::Line Item count for IR:%s ",classname,lineCount);
								Log.customer.debug("%s::IR_periodEndDate:%s",classname,periodEndDate);
								Log.customer.debug("%s::IR_datasetFacilityCode:%s",classname,datasetFacilityCode);
								//outPW_FlatFile.write(periodEndDate);
								//outPW_FlatFile.write(datasetFacilityCode);
								partitionNumber = invrecon.getPartitionNumber();
								//String partition_name = String(partitionName);
								Log.customer.debug("%s::IR_Partition:%s",classname,partitionNumber);

							//	Source-Of-Record Facility if Ezopen IR.FacilityFlag



								if (partitionNumber==4){

									//Date pSD = (Date)invrecon.getFieldValue("BlockStampDate");
									//Kannan, As per new Doc
									Date pSD = (Date)invrecon.getFieldValue("ControlDate");
									periodStartdate = DateFormatter.toYearMonthDate(pSD);
									Log.customer.debug("%s::Period Start Date:%s",pSD,classname);
									partitionName = "Ezopen";
									ihBookdate = DateFormatter.toYearMonthDate(pSD);

								}
								 if (lineCount > 0){
									irLineItemVector = irLineItem.size();
									for (int i =0; i<irLineItemVector;i++){
										// Writing Partition - 1
										LineItem irli = (LineItem)irLineItem.get(i);
										Log.customer.debug("%s::Writing Partition@@@@@@@%s",classname,partitionName);
										outPW_FlatFile.write(partitionName+"~|");

										// Writing Source-Of-Record Facility
										/*sourceOfRecord = (String)invrecon.getFieldValue("FacilityFlag");
										if (!StringUtil.nullOrEmptyOrBlankString(sourceOfRecord)){
											Log.customer.debug("%s;;Ir FacilityFlag:%s",classname,sourceOfRecord);
											outPW_FlatFile.write(sourceOfRecord+"~|");
										}
										else {
											outPW_FlatFile.write("~|");
								        }
                                         */
                                         outPW_FlatFile.write("36~|");

                                        //Writing Record-Type - 2
										//outPW_FlatFile.write(recordType+"~|");
										// Writing File-Layout-Version with filler - 3
										//outPW_FlatFile.write(fileLayoutVersion+"~|"+"M"+"~|");
										irUniqueName = (String)invrecon.getFieldValue("UniqueName");
										Log.customer.debug("%s::irUniqueName: %s",classname,irUniqueName);
										// Writing IH-Invoice-No - 4
										//Start: Q4 2013 - RSD117 - FDD 2/TDD 2
										//outPW_FlatFile.write(irUniqueName+"~|");										
										
										String SupInvNumber = (String)invrecon.getDottedFieldValue("Invoice.InvoiceNumber");
										Log.customer.debug("CATEzopenDWInvoicePush_FlatFile: SupInvNumber: "+ SupInvNumber);
										int invleng = SupInvNumber.length();
										if (invleng > 34)
										{	
											String SupInvNumber1 = getFormatattedTxt(SupInvNumber);
											int invleng1 = SupInvNumber1.length();
											Log.customer.debug("CATEzopenDWInvoicePush_FlatFile: invleng1: "+ invleng1);
											String FormattedirUniqueName = getFormatattedTxt(irUniqueName, invleng1);
											Log.customer.debug("CATEzopenDWInvoicePush_FlatFile: FormattedirUniqueName: "+ FormattedirUniqueName);
											// Writing IH-Invoice-No - 4
											outPW_FlatFile.write(FormattedirUniqueName+"~|");
										}
										else
										{
											String FormattedirUniqueName = getFormatattedTxt(irUniqueName, invleng);
											Log.customer.debug("CATEzopenDWInvoicePush_FlatFile: FormattedirUniqueName: "+ FormattedirUniqueName);
											// Writing IH-Invoice-No - 4
											outPW_FlatFile.write(FormattedirUniqueName+"~|");
										}										
										//End: Q4 2013 - RSD117 - FDD 2/TDD 2

										irSupplierLocation = (String)invrecon.getDottedFieldValue("SupplierLocation.UniqueName");
										if (irSupplierLocation != null){
											Log.customer.debug("%s::irSupplierLocation:%s",classname,irSupplierLocation);
											// Writing IH-Supplier-Code - 5
											outPW_FlatFile.write(irSupplierLocation+"~|");
										}
										else{
											outPW_FlatFile.write("~|");
										}
										Date irSuppinvDate = (Date)invrecon.getFieldValue("SupplierInvoiceDate");
										if (irSuppinvDate!=null){
											irInvdateyymmdd = DateFormatter.toYearMonthDate(irSuppinvDate);
											Log.customer.debug("%s::Invoice Date:%s",classname,irInvdateyymmdd);
											// Writing IH-Invoice-Date - 6
											outPW_FlatFile.write(irInvdateyymmdd+"~|");
										}
										else{
											outPW_FlatFile.write("~|");
										}
										//Log.customer.debug("%s::ihRecordStatus:%s",classname,ihRecordStatus);
										// Writing IH-Record-Status - 7
										//outPW_FlatFile.write(ihRecordStatus+"~|");
										irTotalcost = (Money)invrecon.getFieldValue("TotalCost");
										if (irTotalcost!=null){
											irTotalCostAmount = (BigDecimal)invrecon.getDottedFieldValue("TotalCost.Amount");
											Log.customer.debug("%s::Total cost amount:%s",classname,irTotalCostAmount);
											if (irTotalCostAmount!=null) {
												//Writing IH-Invoice-Type - 8
												int Ir_tca = irTotalCostAmount.intValue();
												if (Ir_tca >= 0) {
													outPW_FlatFile.write("01"+"~|");
												}
												else if (Ir_tca < 0){
													outPW_FlatFile.write("02"+"~|");
												}
												// Writing IH-Book-Date - 9
												outPW_FlatFile.write(ihBookdate+"~|");
												irTotalCostCurrency = irTotalcost.getCurrency().getUniqueName();
												if (!StringUtil.nullOrEmptyOrBlankString(irTotalCostCurrency)){
													//Writing IH-Currency-Code - 10
													Log.customer.debug("%s::TotalCost Currency:%s",classname,irTotalCostCurrency);
													outPW_FlatFile.write(irTotalCostCurrency+"~|");
												}
												else {
													outPW_FlatFile.write("~|");
												}
												irStngTotalCost = BigDecimalFormatter.getStringValue(irTotalCostAmount);
												Log.customer.debug("%s::Writing IR total cost:%s",classname,irStngTotalCost);
												// Writin IH-Total-Invoice-Amount - 11
												outPW_FlatFile.write(irStngTotalCost+"~|");
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
										    // // Writing IH-PO-Discount-Amount - 12
											if (irTotalInvoiceDiscountDollarAmount!=null) {
												irTIDDA = irTotalInvoiceDiscountDollarAmount.intValue();
												Log.customer.debug("%s::TotalInvoiceDiscountDollarAmount integer value:%s",classname,irTIDDA);
												if (irTIDDA==0){
													outPW_FlatFile.write("0000000000000.00"+"~|");
												}
												else {
													String strg_DiscountAmount = BigDecimalFormatter.getStringValue(irTotalInvoiceDiscountDollarAmount);
													Log.customer.debug("%s::Writting the discount amount:%s",classname,strg_DiscountAmount);
													outPW_FlatFile.write(strg_DiscountAmount+"~|");
												}

											}
											else {
												Log.customer.debug("%s::No value for irTotalInvoiceDiscountDollarAmount so leaving it blanck");
												outPW_FlatFile.write("~|");
											}
											InvoiceReconciliationLineItem irLineItem_2 = (InvoiceReconciliationLineItem)irLineItem.get(i);
											irNumberInCollection = irLineItem_2.getNumberInCollection();
											numberInCollection = IntegerFormatter.getStringValue(irNumberInCollection);
											Log.customer.debug("%s::IR number in collection:%s",classname,numberInCollection);
											// Writing IL-Line-Seq-No - 13
											outPW_FlatFile.write(numberInCollection+"~|");
											irLineDescritpiton = irLineItem_2.getDescription();
											// Writing IL-Item-No - 14
											// Writing IL-Item-Class-Code - 15
											// Writing IL-Item-Description - 16
											// Writing IL-UNSPSC-Code - 17
											// Writing IL-Supplier-Item-No - 18
											if (irLineDescritpiton!=null){
												Log.customer.debug("%s::IR_LineDescription:%s",classname,irLineDescritpiton);
												irDescItemNumber = irLineDescritpiton.getItemNumber(); //what if this is blanck, right now considering blanck also as null
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
												descDescription_temp = (String)irLineDescritpiton.getFieldValue("Description");
												descDescription = descDescription_temp.replaceAll("[^\\p{ASCII}]", "");
												if (!StringUtil.nullOrEmptyOrBlankString(descDescription)){
													//String ir_Description_Stripped = StringUtil.replaceCharByChar(desc_Description,'\n',' ');
													descDescription = StringUtil.replaceCharByChar(descDescription,'\r',' ');
													descDescription = StringUtil.replaceCharByChar(descDescription,'\t',' ');
													descDescription = StringUtil.replaceCharByChar(descDescription,'\n',' ');
													Log.customer.debug("%s::Description:%s",classname,descDescription);
													outPW_FlatFile.write(descDescription+"~|");
												}
												else{
													outPW_FlatFile.write("~|");
												}
												irCommodityCode = irLineDescritpiton.getCommonCommodityCode();
												Log.customer.debug("%s::IR Description CommodityCode:%s",classname,irCommodityCode);
												if (irCommodityCode!=null){
													ccUniqueName = irCommodityCode.getUniqueName();
													Log.customer.debug("%s::IR Description CommodityCode UniqueName:%s",classname,ccUniqueName);
													outPW_FlatFile.write(ccUniqueName+"~|");
												}
												else {
													outPW_FlatFile.write("~|");
												}
												descSupplierPartNumber = irLineDescritpiton.getSupplierPartNumber();
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
											 irLineType = irLineItem_2.getLineType();
											 // Writing IL-Line-Type - 19
											 if(irLineType!=null){
												category = irLineType.getCategory();
												if (category==1){
													outPW_FlatFile.write("01"+"~|");
												}
												else if (category==2){
													String irLineTypeString = (String)irLineType.getFieldValue("UniqueName");
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

											 Boolean isHaz = (Boolean)irLineItem_2.getFieldValue("IsHazmat");
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
											 irHazmat = BooleanFormatter.getStringValue(irLineItem_2.getFieldValue("IsHazmat"));
											 Log.customer.debug("%s::Ir Hazmat value:%s",classname,irHazmat);
											 // Writing IL-Hazardous-Item-Ind - 20
											 if (!StringUtil.nullOrEmptyOrBlankString(irHazmat)){
												if (irHazmat=="true"){
													outPW_FlatFile.write("3"+"~|");
												}
												else {
													outPW_FlatFile.write("0"+"~|");
												}
											 }
											 else {
												outPW_FlatFile.write("~|");
											 }

											 */
											 irLineQuantity = BigDecimalFormatter.getStringValue(irLineItem_2.getQuantity());
											 // Writing IL-Bill-Qty - 21
											 if (!StringUtil.nullOrEmptyOrBlankString(irLineQuantity)){
												Log.customer.debug("%s::IR LineQuantity:%s",classname,irLineQuantity);
												outPW_FlatFile.write(irLineQuantity+"~|");
											 }
											 else {
												outPW_FlatFile.write("~|");
											 }
											 // Writing IL-Bill-Qty-Unit-Of-Measure - 22
											 //irUOM = (UnitOfMeasure)irLineDescritpiton.getUnitOfMeasure();
											 String irUOM_UniqueName = null;
											 if (irLineItem_2.getDottedFieldValue("Description.UnitOfMeasure") !=null){
												 String uOMUniqueName = (String)irLineItem_2.getDottedFieldValue("Description.UnitOfMeasure.UniqueName");

												 //String irUOM_UniqueName = 	irUOM.getFieldValue("CAPSUnitOfMeasure").toString();
												//String irUOM_UniqueName = 	irUOM.getFieldValue("CAPSUnitOfMeasure");
												Object irUOM_object = 	irLineItem_2.getDottedFieldValue("Description.UnitOfMeasure.CAPSUnitOfMeasure");
												    if(irUOM_object != null) {
							                        irUOM_UniqueName = irUOM_object.toString();
												Log.customer.debug("%s::IR Desc UOM:%s",classname,irUOM_UniqueName);
												if (!StringUtil.nullOrEmptyOrBlankString(irUOM_UniqueName)){
													outPW_FlatFile.write(irUOM_UniqueName+"~|");
												}
												else {
													//	IF CAPSUnitOfMeasure = Empty  THEN LineItems.Description.UnitOfMeasure.UniqueName
													if (!StringUtil.nullOrEmptyOrBlankString(uOMUniqueName))
											        outPW_FlatFile.write(uOMUniqueName+"~|");
												}
											}
												else {
													// IF CAPSUnitOfMeasure = NULL  THEN LineItems.Description.UnitOfMeasure.UniqueName
													if (!StringUtil.nullOrEmptyOrBlankString(uOMUniqueName))
											        outPW_FlatFile.write(uOMUniqueName+"~|");
												}
											 }
											 else {
												outPW_FlatFile.write("~|");
											 }
											 // Writing IL-Currency-Code - 23
											 if (irTotalcost!=null){
												irTotalCostCurrency = irTotalcost.getCurrency().getUniqueName();
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
											 // Writing IL-Unit-Price - 24
											 irdecsPrice =  BigDecimalFormatter.getStringValue(irLineDescritpiton.getPrice().getAmount());
											 Log.customer.debug("%s::Ir desc Price:%s",classname,irdecsPrice);
											 if (!StringUtil.nullOrEmptyOrBlankString(irdecsPrice)){
												outPW_FlatFile.write(irdecsPrice+"~|");
											 }
											 else {
												outPW_FlatFile.write("~|");
											 }
											 // Writing IL-Unit-Price-Unit-of-Measure - 25
											 //irUOM = (UnitOfMeasure)irLineDescritpiton.getUnitOfMeasure();
											 String irUOM_UniqueName2 = null;
												if (irLineItem_2.getDottedFieldValue("Description.UnitOfMeasure") !=null){
													String uOMUniqueNameLi = (String)irLineItem_2.getDottedFieldValue("Description.UnitOfMeasure.UniqueName");
													//Object irUOM_object = 	irUOM.getFieldValue("CAPSUnitOfMeasure");
													Object irUOM_object = 	irLineItem_2.getDottedFieldValue("Description.UnitOfMeasure.CAPSUnitOfMeasure");
                                                    if(irUOM_object != null) {
							                        irUOM_UniqueName2 = irUOM_object.toString();
													Log.customer.debug("%s::IR Desc UOM:%s",classname,irUOM_UniqueName2);
													if (!StringUtil.nullOrEmptyOrBlankString(irUOM_UniqueName2)){
														outPW_FlatFile.write(irUOM_UniqueName2+"~|");
													}
													else {
//														 IF CAPSUnitOfMeasure = Empty  THEN LineItems.Description.UnitOfMeasure.UniqueName
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
												// Writing IL-Unit-Price-Qty-Conversion-Factor - 26
												outPW_FlatFile.write("1.0"+"~|");
												//Writing IL-Extended-Price - 27
												irLineAmount = BigDecimalFormatter.getStringValue(irLineItem_2.getAmount().getAmount());
												if (!StringUtil.nullOrEmptyOrBlankString(irLineAmount)) {
													Log.customer.debug("%s::Ir Line Amount:%s",classname,irLineAmount);
													outPW_FlatFile.write(irLineAmount+"~|");
												}
												else {
													outPW_FlatFile.write("~|");
												}
												// Writing IL-Discount-Percent - 27
												outPW_FlatFile.write("00000.0000"+"~|");
												irLineCapsCharge = (ClusterRoot)irLineItem_2.getFieldValue("CapsChargeCode");
												// Writing IL-Charge-Type - 28
												// As per Ann's new Doc IL-Charge-Type Not applicable for Ezopen, so add ~|

												/*if (irLineCapsCharge!=null){
													Log.customer.debug("%s::Ir Line caps charge code:%s",classname,irLineCapsCharge);
													String ir_CapsUniqueName = (String)irLineCapsCharge.getFieldValue("UniqueName");
													Log.customer.debug("%s::Ir Line caps charge code UniqueName:%s",classname,ir_CapsUniqueName);
													outPW_FlatFile.write(ir_CapsUniqueName+"~|");
												}
												else {
													outPW_FlatFile.write("~|");
												} */

												outPW_FlatFile.write("~|");

												irLinePoNumber = (String)irLineItem_2.getFieldValue("PONumber");
												// Writing IL-PO-No - 29
												if (!StringUtil.nullOrEmptyOrBlankString(irLinePoNumber)){
													Log.customer.debug("%s::IR Line PO Number:%s",classname,irLinePoNumber);
													irLinePoNumber = StringUtil.replaceCharByChar(irLinePoNumber,'\r',' ');
													irLinePoNumber = StringUtil.replaceCharByChar(irLinePoNumber,'\t',' ');
													irLinePoNumber = StringUtil.replaceCharByChar(irLinePoNumber,'\n',' ');
													outPW_FlatFile.write(irLinePoNumber+"~|");
												}
												else {
													outPW_FlatFile.write("~|");
												}
												// Writing IL-PO-Line-Seq-No - 30
												irLinePoLineNumber = IntegerFormatter.getIntValue(irLineItem_2.getFieldValue("POLineItemNumber"));
												irLinePoLinenoString = IntegerFormatter.getStringValue(irLinePoLineNumber);
												Log.customer.debug("%s::Line po Line number:%s",classname,irLinePoLinenoString);
												if (!StringUtil.nullOrEmptyOrBlankString(irLinePoLinenoString) && !irLinePoLinenoString.equals("0")){
													Log.customer.debug("%s::Ir Line Po Line Number:%s",classname,irLinePoLineNumber);
													outPW_FlatFile.write(irLinePoLinenoString+"~|");
												}
												else {

													// IF IR.LineItems. POLineItemNumber is NULL THEN SEND VALUE OF 1
													outPW_FlatFile.write("1"+"~|");
												}
												//Writing IL- Spend-Category - 31
												if (irLinePoNumber!=null){
													if (StringUtil.startsWithIgnoreCase(irLinePoNumber , "C")){
														outPW_FlatFile.write("C"+"~|");
													 }
													 else {
													 	outPW_FlatFile.write("P"+"~|");
													 }
												}
												else {
													outPW_FlatFile.write("N"+"~|");
												}
												partitionNumber2 = invrecon.getPartitionNumber();
												Log.customer.debug("%s::partiton number 2nd place:%s",classname,partitionNumber2);
												if (partitionNumber2==4){
													irAccounting = irLineItem_2.getAccountings();
													if (irAccounting!=null){
														irSplitAccounting = (BaseVector)irAccounting.getSplitAccountings();
														irSplitaccSize = irSplitAccounting.size();
														Log.customer.debug("%s::Split acc size:%s",classname,irSplitaccSize);
														if (irSplitaccSize > 0){
															Log.customer.debug ("%s::getting accounting facility",classname);

															for (int j = 0; j<irSplitaccSize;j++){
																SplitAccounting irSplitAccounting2 = (SplitAccounting)irSplitAccounting.get(0);
																// As per Ann's new Doc
																String irAccountingFacility = (String)irSplitAccounting2.getDottedFieldValue("AccountingFacility");
																String irAccountingRecFacility = (String)irLineItem_2.getDottedFieldValue("ShipTo.ReceivingFacility");
																irDepartment = (String)irSplitAccounting2.getFieldValue("Department");
																//Log.customer.debug("%s::Accounting facility:%s",classname,irAccountingFacility);
																irOrder = (String)irSplitAccounting2.getFieldValue("Order");
																irDivision = (String)irSplitAccounting2.getFieldValue("Division");
																irSection = (String)irSplitAccounting2.getFieldValue("Section");
																irExpenseAccount = (String)irSplitAccounting2.getFieldValue("ExpenseAccount");
																irMisc = (String)irSplitAccounting2.getFieldValue("Misc");

																/*
																if(!StringUtil.nullOrEmptyOrBlankString(irAccountingFacility)){
																	Log.customer.debug("%s::Accounting facility:%s",classname,irAccountingFacility);
																	irAccFac = irAccountingFacility+"~|";
																	//outPW_FlatFile.write(irAccountingFacility+"~|");
																}
																else {
																	irAccFac = ("~|");
																	// outPW_FlatFile.write("~|"+"\n");
																}
																*/
																irAccFac = "36"+"~|";
 																/*
																if(!StringUtil.nullOrEmptyOrBlankString(irAccountingFacility)){
																	ilRecvFacilityCode = irAccountingFacility+"~|";
																}
																else {
																	ilRecvFacilityCode = ("AA"+"~|");
																}
																*/

																if(!StringUtil.nullOrEmptyOrBlankString(irAccountingRecFacility)){
																		ilRecvFacilityCode = irAccountingRecFacility+"~|";
																	}
																	else {
																		ilRecvFacilityCode = ("AA"+"~|");
																}


																if(!StringUtil.nullOrEmptyOrBlankString(irAccountingFacility)){
																			Log.customer.debug("%s::Accounting facility:%s",classname,irAccountingFacility);
																			irAccRecFac = irAccountingFacility+"~|";
																			//outPW_FlatFile.write(irAccountingFacility+"~|");
																		}
																		else {
																			irAccRecFac = ("~|");
																			// outPW_FlatFile.write("~|"+"\n");
																}


																if(!StringUtil.nullOrEmptyOrBlankString(irDepartment)) {
																	irDepartment = irDepartment+"~|";
																}
																else {
																	irDepartment = ("~|");
																}
																if (irDivision!=null){
																	Log.customer.debug("%s::IR Division:%s",classname,irDivision);
																	irDivisionString = irDivision+"~|";
																}
																else {
																	irDivisionString = ("~|");
																}
																if(irSection!=null){
																	Log.customer.debug("%s::IR Section:%s",classname,irSection);
																	irSectionString = irSection+"~|";
																}
																else {
																	irSectionString = ("~|");
																}
																if (irExpenseAccount!=null){
																	Log.customer.debug("%s::IR Expense Account:%s",classname,irExpenseAccount);
																	irExpenseAccountString  = irExpenseAccount+"~|";
																}
																else {
																	irExpenseAccountString = ("~|");
																}
																if (!StringUtil.nullOrEmptyOrBlankString(irOrder)){
																	Log.customer.debug("%s::Accounting Order:%s",classname,irOrder);
																	irOrderName = irOrder+"~|";
																}
																else {
																	irOrderName =("~|");
																}
																if (irMisc!=null){
																	Log.customer.debug("%s::IR Misc:%s",classname,irMisc);
																	irMiscString = irMisc+"~|";
																}
																else {
																	irMiscString = ("~|");
																}
															  }
															   outPW_FlatFile.write(irAccFac);
															   outPW_FlatFile.write(ilRecvFacilityCode);
															   outPW_FlatFile.write(irAccRecFac);
															   //outPW_FlatFile.write(irAccRecFac);
															   outPW_FlatFile.write(irDepartment);
															   outPW_FlatFile.write(irDivisionString);
															   outPW_FlatFile.write(irSectionString);
															   outPW_FlatFile.write(irExpenseAccountString);
															   outPW_FlatFile.write(irOrderName);
															   outPW_FlatFile.write(irMiscString);
															   outPW_FlatFile.write(irAccFac);
															   //outPW_FlatFile.write("U9");
															   irBuyerCode = (ClusterRoot)irLineItem_2.getFieldValue("BuyerCode");
															   if (irBuyerCode!=null){
															   	ir_buyercc = (String)irBuyerCode.getFieldValue("BuyerCode");
															   	if(!StringUtil.nullOrEmptyOrBlankString(ir_buyercc)){
															   		Log.customer.debug("%s:: IR Buyer Code:%s",classname,ir_buyercc);
															   		//outPW_FlatFile.write("U9"+"~|");
															   		outPW_FlatFile.write(ir_buyercc);
															   		//outPW_FlatFile.write("\n");
															   	}
															   	else {
															   		//outPW_FlatFile.write("~|");
															   		//outPW_FlatFile.write("U9");
															   		//outPW_FlatFile.write("\n");
															   	}
															  }
															  else {
															  	irLineOrder = (DirectOrder)irLineItem_2.getOrder();
															   	Log.customer.debug("%s::IR line Purchase Order:%s",classname,irLineOrder);
															   	if (irLineOrder!=null){
															   		irorderLineItem = (ariba.purchasing.core.POLineItem)irLineItem_2.getOrderLineItem();
															   		Log.customer.debug("%s::Order Line Item:%s",classname,irorderLineItem);
															   		if (irorderLineItem!=null){
															   			irPOLineBuyerCode = (ClusterRoot)irorderLineItem.getFieldValue("BuyerCode");
															   			if (irPOLineBuyerCode!= null){
															   				irPOlinebuyercc = (String)irPOLineBuyerCode.getFieldValue("BuyerCode");
															   				Log.customer.debug("%s::Ir PO Line Buyer Code");
															   				//outPW_FlatFile.write("U9"+"~|");
															   				outPW_FlatFile.write(irPOlinebuyercc);
															   				//outPW_FlatFile.write("\n");
															   			}
															   			else{
															   				//outPW_FlatFile.write("~|");
															   				//outPW_FlatFile.write("U9");
															   				//outPW_FlatFile.write("\n");
															   			}
															   		}
															   		else{
															   			//outPW_FlatFile.write("~|");
															   			//outPW_FlatFile.write("U9");
															   			//outPW_FlatFile.write("\n");
															   		}

															   	}
															   	else{
															   		//outPW_FlatFile.write("~|");
															   		//outPW_FlatFile.write("U9");
															   		//outPW_FlatFile.write("\n");
															   	}
											                  }
														  }
														  else{
															outPW_FlatFile.write("~|");
															outPW_FlatFile.write("~|");
															outPW_FlatFile.write("~|");
															outPW_FlatFile.write("~|");
															outPW_FlatFile.write("~|");
															outPW_FlatFile.write("~|");
															outPW_FlatFile.write("~|");
															outPW_FlatFile.write("~|");
															outPW_FlatFile.write("~|");
															//outPW_FlatFile.write("U9");
															//outPW_FlatFile.write("~|");
															//outPW_FlatFile.write("\n");
														  }
													}
													else {
														outPW_FlatFile.write("~|");
														outPW_FlatFile.write("~|");
														outPW_FlatFile.write("~|");
														outPW_FlatFile.write("~|");
														outPW_FlatFile.write("~|");
														outPW_FlatFile.write("~|");
														outPW_FlatFile.write("~|");
														outPW_FlatFile.write("~|");
														outPW_FlatFile.write("~|");
														//outPW_FlatFile.write("U9");
														//outPW_FlatFile.write("~|");
														//outPW_FlatFile.write("\n");
													}
													/*outPW_FlatFile.write("~|");
													outPW_FlatFile.write("~|");
													outPW_FlatFile.write("~|");
													outPW_FlatFile.write("U9"+"\n"); */
												}
												else {
													irAccounting = irLineItem_2.getAccountings();
													if (irAccounting!=null){
														irSplitAccounting = (BaseVector)irAccounting.getSplitAccountings();
														irSplitaccSize = irSplitAccounting.size();
														if (irSplitaccSize > 0){

															for (int k = 0; k<irSplitaccSize;k++){
																irSplitAccounting2 = (SplitAccounting)irSplitAccounting.get(0);
																irFacility = (ClusterRoot)irSplitAccounting2.getFieldValue("Facility");
																irFacilityUniqueName = (String)irFacility.getFieldValue("UniqeuName");
																if(!StringUtil.nullOrEmptyOrBlankString(irFacilityUniqueName)){
																	Log.customer.debug("%s:: IR Facility:%s",classname,irFacilityUniqueName);
																	irFac = irFacilityUniqueName+"~|";
																	//outPW_FlatFile.write(irFacilityUniqueName+"\n");
																 }
																 else {
																	irFac = ("~|");
																	//outPW_FlatFile.write("~|"+"\n");
																 }

															 }
															 outPW_FlatFile.write(irFac);
														}
													}
												}
												//32 IsAdHoc - catalog or non catalog, Issue #269 - Dharshan
												isAdHocBoolean = true;
												isAdHoc = null;
												if (irli.getDottedFieldValue("IsAdHoc") != null) {
													isAdHoc = (Boolean) irli.getDottedFieldValue("IsAdHoc");
													isAdHocBoolean = BooleanFormatter.getBooleanValue(isAdHoc);
													Log.customer.debug("%s::isAdHocBoolean:%s",classname,isAdHocBoolean);
													if(isAdHocBoolean == false){
														outPW_FlatFile.write("~|Catalog Item:");
													}
													else
													{
														outPW_FlatFile.write(" ~|");
														Log.customer.debug("%s::isAdHocBoolean is true, not catalog item",classname);
													}
												}
												else {Log.customer.debug("%s::isAdHocBoolean is null, leave blank",classname);
												outPW_FlatFile.write(" ~|");
												}

												outPW_FlatFile.write("\n");
									}
									totalNumberOfIrWritten++;
									//Update DWPOFlag in DO based on config
									if(isCompletedFlgUpdate) {
										Log.customer.debug("%s::EzopenIRFlagAction is Completed setting DWInvoiceFlag ...", classname);
										invrecon.setFieldValue("DWInvoiceFlag", "Completed");
									}
									else {
										Log.customer.debug("%s::EzopenIRFlagAction is None no action req DWInvoiceFlag ...", classname);
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
				message.append("CATEzopenDWInvoicePush_FlatFile Task Failed - Exception details below");
				message.append("\n");
				message.append(e.toString());
				mailSubject = "CATEzopenDWInvoicePush_FlatFile Task Failed";
				Log.customer.debug("%s: Inside Exception message "+ message.toString() , classname);
				new ScheduledTaskException("Error : " + e.toString(), e);
                throw new ScheduledTaskException("Error : " + e.toString(), e);
			 }
			  finally {

				//Change made by Soumya begins
				Log.customer.debug("%s::Archive File Path:%s",classname,archiveFileDataPath);
				Log.customer.debug("CATEzopenDWInvoicePush_FlatFile:Starting Copying the flat file to Archive ");
				CATFaltFileUtil.copyFile(flatFilePath, archiveFileDataPath);
				Log.customer.debug("CATEzopenDWInvoicePush_FlatFile:Completed Copying the flat file to Archive ");

				try
				{
					Log.customer.debug("CATEzopenDWInvoicePush_FlatFile:Changing file permission of Data file.");
					Runtime.getRuntime().exec("chmod 666 " + flatFilePath);
					Log.customer.debug("CATEzopenDWInvoicePush_FlatFile:Changed file permission of Data file.");
				}catch (IOException e1) {
					Log.customer.debug("Error in changing Permission. "+ e1);
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
		int rellength = txtLength + 4; //Inv Number + 'IRG' + '-'
		int temp = fulllength - rellength;
		String formattedTxt = "";
		Log.customer.debug("CATEzopenDWInvoicePush_FlatFile: int temp  " + temp);
		inputTxt = inputTxt.substring(rellength, fulllength);
		formattedTxt = "IRG-" + inputTxt;
		Log.customer.debug("CATEzopenDWInvoicePush_FlatFile: formattedTxt " + formattedTxt);
		return formattedTxt;
		}

	private String getFormatattedTxt(String inputTxt2) {
		inputTxt2 = inputTxt2.substring(0,34);
		Log.customer.debug("CATEzopenDWInvoicePush_FlatFile: inputTxt2 " + inputTxt2);
		return inputTxt2;
		}
	//End: Q4 2013 - RSD117 - FDD 2/TDD 2

	public CATEzopenDWInvoicePush_FlatFile(){

	}

	}