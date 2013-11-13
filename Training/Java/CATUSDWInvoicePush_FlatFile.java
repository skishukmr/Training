
/*******************************************************************************************************************************************
	Creator: Madhavan Chari
	Description: Writing the feilds from the IR to flat file
	ChangeLog:
	Date		Name		History
	--------------------------------------------------------------------------------------------------------------
   Deepak Sharma
      Date : 22/08/2008
   Changing: CAPSUOM, Commit size

    29/11/2011 IBM AMS Vikram Singh				Filtering non ASCII characters
	15/06/2012 Dharshan   Issue #269	 IsAdHoc - catalog or non catalog,
	10/07/2013 IBM AMS Vikram Singh		 Q4 2013 - RSD117 - FDD 2/TDD 2 - Modify IR uniqueName when sending the IR number to PDW

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

import ariba.approvable.core.LineItem;
import ariba.base.core.Base;
import ariba.base.core.BaseId;
import ariba.base.core.BaseVector;
import ariba.base.core.ClusterRoot;
import ariba.base.core.Log;
import ariba.base.core.Partition;
import ariba.base.core.aql.AQLOptions;
import ariba.base.core.aql.AQLQuery;
import ariba.base.core.aql.AQLResultCollection;
import ariba.base.core.aql.AQLUpdate;
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
import ariba.util.formatter.BooleanFormatter;
import ariba.util.formatter.DateFormatter;
import ariba.util.formatter.IntegerFormatter;
import ariba.util.scheduler.ScheduledTask;
import ariba.util.scheduler.ScheduledTaskException;
import ariba.util.scheduler.Scheduler;
import config.java.common.CatEmailNotificationUtil;
//change made by Soumya begins
import config.java.schedule.util.CATFaltFileUtil;
//change made by Soumya ends

//TBD:::Whats needs to be filled if any of the field value is not present. right now writing it with ""
public class CATUSDWInvoicePush_FlatFile extends ScheduledTask
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
    private String classname="CATUSDWInvoicePush_FlatFile";
    private Calendar calendar = new GregorianCalendar();
	private	java.util.Date date = calendar.getTime();
	private	DateFormat dateFormat = new SimpleDateFormat("MM_dd_yyyy");
	private	String fileExt = ""+ dateFormat.format(date);
    private String flatFilePath = "/msc/arb9r1/downstream/catdata/DW/MSC_DW_INVOICE_PUSH_US_"+fileExt+".txt";
	//change made by soumya begins

	private String archiveFileDataPath = "/msc/arb9r1/downstream/catdata/DW/archive/MSC_DW_INVOICE_PUSH_US_ARCHIVE"+fileExt+".txt";

	//change made by soumya ends

    private String periodenddate = null;
    private String datasetFacilityCode = "Z1";
    private String RecordType = "IN";
    private String FileLayoutVersion = "8.1.1";
    private String PeriodStartdate= null;
    private String partitionName= null;
    private String IHRecordStatus = "A";
    private String IHBookdate = null;
    private BaseVector IrLineItem=null;
    private LineItemProductDescription IrLineDescritpiton = null;
    private String irLinePoNumber = null;
    private int partitionNumber;
    private int totalNumberOfIrs;
    private int totalNumberOfIrWritten = 0;
    private FastStringBuffer message = null;
	private String mailSubject = null;
	private String startTime, endTime;
	private String USIRFlagAction = null;
	private boolean isCompletedFlgUpdate = false;
	private int lineCount,IrLineItemvector,IrnumberInCollection,irLinePoLineNumber,irSplitaccSize;
	private String irUniqueName = null;
	private String irSupplierLocation = null;
	private String irinvdateyymmdd = null;
	private Money irTotalcost = null;
	private BigDecimal Irtotalcostamount;
	private String irTotalCostCurrency = null;
	private String irStngTotalCost = null;
	private BigDecimal irTotalInvoiceDiscountDollarAmount;
	private InvoiceReconciliationLineItem IrLineItem2 = null;
	private String numberInCollection = null;
	private String irDescItemNumber = null;
	private String descDescription = null;
	private String descDescription_temp = null;
	private CommodityCode irCommodityCode = null;
	private String ccUniqueName = null;
	private String descSupplierPartNumber = null;
	private  ProcureLineType IrLineType = null;
	private String irHazmat = null;
	private String IrLineQuantity = null;
	private UnitOfMeasure IrUOM = null;
	private String irUOMUniqueName = null;
	private String IrdecsPrice = null;
	private String ir_LineAmount = null;
	private ClusterRoot  irLineCapsCharge = null;
	private String irCapsUniqueName = null;
	private String irLinePoLinenoString = null;
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
	private String irOrder	= null;
	private String irDivision	= null;
	private String irSection	= null;
	private String irExpenseAccount	= null;
	private String irMisc	= null;
	private  ClusterRoot irBuyerCode = null;
	private String irbuyercc = null;
	private DirectOrder irLineOrder = null;
	private ariba.purchasing.core.POLineItem irOrderLineItem = null;
	private ClusterRoot irPOLineBuyerCode = null;
	private String irPOlinebuyercc = null;
	private ariba.invoicing.core.InvoiceReconciliation invrecon = null;
	private PrintWriter outPW_FlatFile = null;
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
	Log.customer.debug("%s::Start of CATUSDWInvoicePush_FlatFile task ...",classname);
	// Read the USPOFlagAction info from cat.dwAction.util
	USIRFlagAction = ResourceService.getString("cat.dwAction", "USIRFlagAction");
	Log.customer.debug("%s::USIRFlagAction ...", USIRFlagAction, classname);
    if (USIRFlagAction!=null && ! USIRFlagAction.equals("None") && USIRFlagAction.indexOf("not found")==-1)
		isCompletedFlgUpdate = false;
	if ( USIRFlagAction!=null && USIRFlagAction.equals("Completed")&& USIRFlagAction.indexOf("not found")==-1)
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
				outPW_FlatFile = new PrintWriter(IOUtil.bufferedOutputStream(out_FlatFile), true);
				p = Base.getSession().getPartition();
				message = new FastStringBuffer();
				mailSubject = "CATUSDWInvoicePush_FlatFile Task Completion Status - Completed Successfully";
				try
				{
					isHeader = false;
					query = "select from ariba.invoicing.core.InvoiceReconciliation where DWInvoiceFlag = 'InProcess' or DWInvoiceFlag = 'Processing'";
					Log.customer.debug(query);
					AQLQuery aqlquery = null;
					AQLOptions options = null;
					AQLResultCollection results = null;
					aqlquery = AQLQuery.parseQuery(query);
					options = new AQLOptions(p);
					results = Base.getService().executeQuery(aqlquery, options);
					totalNumberOfIrs = results.getSize();
					if(results.getErrors() != null)
					Log.customer.debug("ERROR GETTING RESULTS in Results");
					//List irList = ListUtil.list();
					 int count = 0;
					while(results.next()){
						invrecon = (InvoiceReconciliation)(results.getBaseId("InvoiceReconciliation").get());
					    //irList.add(invrecon);
						if(invrecon != null){
						try{
								IrLineItem = (BaseVector)invrecon.getLineItems();
								Log.customer.debug("%s::Ir Line Item:%s",classname,IrLineItem);
								// Shaila : Issue 754 : To ensure no IR with zero line item is processed
								lineCount = invrecon.getLineItemsCount();
								Log.customer.debug("%s::Line Item count for IR:%s ",classname,lineCount);
								Log.customer.debug("%s::IR_period_end_date:%s",classname,periodenddate);
								Log.customer.debug("%s::IR_datasetFacilityCode:%s",classname,datasetFacilityCode);
								//outPW_FlatFile.write(period_end_date);
								//outPW_FlatFile.write(datasetFacilityCode);
								partitionNumber = invrecon.getPartitionNumber();
								//String partition_name = String(partitionName);
								Log.customer.debug("%s::IR_Partition:%s",classname,partitionNumber);
								if (partitionNumber==2){
									//Date pSD = (Date)invrecon.getFieldValue("BlockStampDate");
									Date pSD = (Date)invrecon.getFieldValue("ControlDate");
									PeriodStartdate = DateFormatter.toYearMonthDate(pSD);
									Log.customer.debug("%s::Period Start Date:%s",pSD,classname);
									partitionName = "pcsv1";
									IHBookdate = DateFormatter.toYearMonthDate(pSD);
								}
								 if (lineCount > 0){
									IrLineItemvector = IrLineItem.size();
									for (int i =0; i<IrLineItemvector;i++){
										LineItem irli = (LineItem)IrLineItem.get(i);
										// Writing Partition - 1
										outPW_FlatFile.write(partitionName+"~|");
										//Writing Record-Type - 2
										outPW_FlatFile.write("U9"+"~|");
										//outPW_FlatFile.write(RecordType+"~|");
										// Writing File-Layout-Version with filler - 3
										//outPW_FlatFile.write(FileLayoutVersion+"~|"+"M"+"~|");
										irUniqueName = (String)invrecon.getFieldValue("UniqueName");
										Log.customer.debug("%s::IRUniqueName: %s",classname,irUniqueName);
										// Writing IH-Invoice-No - 4
										//Start: Q4 2013 - RSD117 - FDD 2/TDD 2
										//outPW_FlatFile.write(irUniqueName+"~|");										
										
										String SupInvNumber = (String)invrecon.getDottedFieldValue("Invoice.InvoiceNumber");
										Log.customer.debug("CATUSDWInvoicePush_FlatFile: SupInvNumber: "+ SupInvNumber);
										int invleng = SupInvNumber.length();
										if (invleng > 33)
										{	
											String SupInvNumber1 = getFormatattedTxt(SupInvNumber);
											int invleng1 = SupInvNumber1.length();
											Log.customer.debug("CATUSDWInvoicePush_FlatFile: invleng1: "+ invleng1);
											String FormattedirUniqueName = getFormatattedTxt(irUniqueName, invleng1);
											Log.customer.debug("CATUSDWInvoicePush_FlatFile: FormattedirUniqueName: "+ FormattedirUniqueName);

											// Writing IH-Invoice-No - 4
											outPW_FlatFile.write(FormattedirUniqueName+"~|");
										}
										else
										{
											String FormattedirUniqueName = getFormatattedTxt(irUniqueName, invleng);
											Log.customer.debug("CATUSDWInvoicePush_FlatFile: FormattedirUniqueName: "+ FormattedirUniqueName);

											// Writing IH-Invoice-No - 4
											outPW_FlatFile.write(FormattedirUniqueName+"~|");

										}
										
										//End: Q4 2013 - RSD117 - FDD 2/TDD 2

										irSupplierLocation = (String)invrecon.getDottedFieldValue("SupplierLocation.UniqueName");
										if (irSupplierLocation != null){
											Log.customer.debug("%s::IR_SupplierLocation:%s",classname,irSupplierLocation);
											// Writing IH-Supplier-Code - 5
											outPW_FlatFile.write(irSupplierLocation+"~|");
										}
										else{
											outPW_FlatFile.write("~|");
										}
										Date Ir_invDate = (Date)invrecon.getFieldValue("InvoiceDate");
										if (Ir_invDate!=null){
											irinvdateyymmdd = DateFormatter.toYearMonthDate(Ir_invDate);
											Log.customer.debug("%s::Invoice Date:%s",classname,irinvdateyymmdd);
											// Writing IH-Invoice-Date - 6
											outPW_FlatFile.write(irinvdateyymmdd+"~|");
										}
										else{
											outPW_FlatFile.write("~|");
										}
										Log.customer.debug("%s::IHRecordStatus:%s",classname,IHRecordStatus);
										// Writing IH-Record-Status - 7
										//outPW_FlatFile.write(IHRecordStatus+"~|");
										irTotalcost = (Money)invrecon.getFieldValue("TotalCost");
										if (irTotalcost!=null){
											Irtotalcostamount = (BigDecimal)invrecon.getDottedFieldValue("TotalCost.Amount");
											Log.customer.debug("%s::Total cost amount:%s",classname,Irtotalcostamount);
											if (Irtotalcostamount!=null) {
												//Writing IH-Invoice-Type - 8
												int Ir_tca = Irtotalcostamount.intValue();
												if (Ir_tca >= 0) {
													outPW_FlatFile.write("01"+"~|");
												}
												else if (Ir_tca < 0){
													outPW_FlatFile.write("02"+"~|");
												}
												// Writing IH-Book-Date/ControlDate - 9
												outPW_FlatFile.write(IHBookdate+"~|");
												irTotalCostCurrency = irTotalcost.getCurrency().getUniqueName();
												if (!StringUtil.nullOrEmptyOrBlankString(irTotalCostCurrency)){
													//Writing IH-Currency-Code - 10
													Log.customer.debug("%s::TotalCost Currency:%s",classname,irTotalCostCurrency);
													outPW_FlatFile.write(irTotalCostCurrency+"~|");
												}
												else {
													outPW_FlatFile.write("~|");
												}
												irStngTotalCost = BigDecimalFormatter.getStringValue(Irtotalcostamount);
												Log.customer.debug("%s::Writing IR total cost:%s",classname,irStngTotalCost);
												// Writin IH-Total-Invoice-Amount - 11
												outPW_FlatFile.write(irStngTotalCost+"~|");
											}
											else {
												outPW_FlatFile.write("~|");
											    outPW_FlatFile.write(IHBookdate+"~|");
												outPW_FlatFile.write("~|");
												outPW_FlatFile.write("~|");
										   }
										}
										else {
											outPW_FlatFile.write("~|");
											outPW_FlatFile.write(IHBookdate+"~|");
											outPW_FlatFile.write("~|");
											outPW_FlatFile.write("~|");
										}
										irTotalInvoiceDiscountDollarAmount = (BigDecimal)invrecon.getDottedFieldValue("TotalInvoiceDiscountDollarAmount.Amount");
										Log.customer.debug("%s::TotalInvoiceDiscountDollarAmount:%s",classname,irTotalInvoiceDiscountDollarAmount);
											if (irTotalInvoiceDiscountDollarAmount!=null) {
												int  ir_TIDDA = irTotalInvoiceDiscountDollarAmount.intValue();
												Log.customer.debug("%s::TotalInvoiceDiscountDollarAmount integer value:%s",classname,ir_TIDDA);
												if (ir_TIDDA==0){
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
											IrLineItem2 = (InvoiceReconciliationLineItem)IrLineItem.get(i);
											IrnumberInCollection = IrLineItem2.getNumberInCollection();
											numberInCollection = IntegerFormatter.getStringValue(IrnumberInCollection);
											Log.customer.debug("%s::IR number in collection:%s",classname,numberInCollection);
											// Writing IL-Line-Seq-No - 13
											outPW_FlatFile.write(numberInCollection+"~|");
											IrLineDescritpiton = IrLineItem2.getDescription();
											// Writing IL-Item-No - 14
											// Writing IL-Item-Class-Code - 15
											// Writing IL-Item-Description - 16
											// Writing IL-UNSPSC-Code - 17
											// Writing IL-Supplier-Item-No - 18
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
											 // Writing IL-Line-Type - 19
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

											/* // Writing IL-Hazardous-Item-Ind - 20
											 if (!StringUtil.nullOrEmptyOrBlankString(irHazmat)){
												if (irHazmat=="true" || irHazmat=="TRUE" || irHazmat.equals("true") || irHazmat.equals("TRUE")){
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
											 // Writing IL-Bill-Qty - 21
											 if (!StringUtil.nullOrEmptyOrBlankString(IrLineQuantity)){
												Log.customer.debug("%s::IR LineQuantity:%s",classname,IrLineQuantity);
												outPW_FlatFile.write(IrLineQuantity+"~|");
											 }
											 else {
												outPW_FlatFile.write("~|");
											 }
											 // Writing IL-Bill-Qty-Unit-Of-Measure - 22
											 //IrUOM = (UnitOfMeasure)IrLineDescritpiton.getUnitOfMeasure();
											 String irUOM_UniqueName = null;

											 if ( IrLineItem2.getDottedFieldValue("Description.UnitOfMeasure") != null){
												 String uOMUniqueName = (String)IrLineItem2.getDottedFieldValue("Description.UnitOfMeasure.UniqueName");
												 Log.customer.debug("%s::uOMUniqueName 1 %s",classname,uOMUniqueName);
												 Object irUOM_object = 	IrLineItem2.getDottedFieldValue("Description.UnitOfMeasure.CAPSUnitOfMeasure");
												 Log.customer.debug("%s::irUOM_object 2 %s",classname,irUOM_object);
											  if(irUOM_object != null) {
							                    irUOM_UniqueName = irUOM_object.toString();
												Log.customer.debug("%s::IR Desc UOM 3:%s",classname,irUOMUniqueName);
												if (!StringUtil.nullOrEmptyOrBlankString(irUOM_UniqueName)){
													outPW_FlatFile.write(irUOM_UniqueName+"~|");
													Log.customer.debug("%s::irUOM_UniqueName writen ti file  4 %s",classname,irUOM_UniqueName);
												}
												else {
//													 IF CAPSUnitOfMeasure = Enpty  THEN LineItems.Description.UnitOfMeasure.UniqueName
													if (!StringUtil.nullOrEmptyOrBlankString(uOMUniqueName))
													outPW_FlatFile.write(uOMUniqueName+"~|");
													Log.customer.debug("%s::CAPSUnitOfMeasure = Enpty 5 %s",classname,uOMUniqueName);
												}
											}
												else {
													// IF CAPSUnitOfMeasure = NULL  THEN LineItems.Description.UnitOfMeasure.UniqueName
													if (!StringUtil.nullOrEmptyOrBlankString(uOMUniqueName))
													outPW_FlatFile.write(uOMUniqueName+"~|");
													Log.customer.debug("%s::CAPSUnitOfMeasure = Enpty 6 %s",classname,uOMUniqueName);
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
											 IrdecsPrice =  BigDecimalFormatter.getStringValue(IrLineDescritpiton.getPrice().getAmount());
											 Log.customer.debug("%s::Ir desc Price:%s",classname,IrdecsPrice);
											 if (!StringUtil.nullOrEmptyOrBlankString(IrdecsPrice)){
												outPW_FlatFile.write(IrdecsPrice+"~|");
											 }
											 else {
												outPW_FlatFile.write("~|");
											 }
											 // Writing IL-Unit-Price-Unit-of-Measure - 25
											 //IrUOM = (UnitOfMeasure)IrLineDescritpiton.getUnitOfMeasure();
											 String irUOM_UniqueName2 = null;
											 if ( IrLineItem2.getDottedFieldValue("Description.UnitOfMeasure") != null){
													//String irUOMUniqueName = 	IrUOM.getFieldValue("CAPSUnitOfMeasure").toString();
													String uOMUniqueNameLi = (String)IrLineItem2.getDottedFieldValue("Description.UnitOfMeasure.UniqueName");
													//Object irUOM_object = 	IrUOM.getFieldValue("CAPSUnitOfMeasure");
													//Object irUOM_object = 	IrUOM.getFieldValue("CAPSUnitOfMeasure");
													Object irUOM_object = IrLineItem2.getDottedFieldValue("Description.UnitOfMeasure.CAPSUnitOfMeasure");
													if(irUOM_object != null) {
							                            irUOM_UniqueName2 = irUOM_object.toString();
													Log.customer.debug("%s::22 IR Desc UOM:%s",classname,irUOM_UniqueName2);
													if (!StringUtil.nullOrEmptyOrBlankString(irUOM_UniqueName2)){
														outPW_FlatFile.write(irUOM_UniqueName2+"~|");
													}
													else {
														//	 IF CAPSUnitOfMeasure = Empty  THEN LineItems.Description.UnitOfMeasure.UniqueName
														if (!StringUtil.nullOrEmptyOrBlankString(uOMUniqueNameLi))
														outPW_FlatFile.write(uOMUniqueNameLi+"~|");
												}
												}
													else {
														// outPW_FlatFile.write("~|");
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
												ir_LineAmount = BigDecimalFormatter.getStringValue(IrLineItem2.getAmount().getAmount());
												if (!StringUtil.nullOrEmptyOrBlankString(ir_LineAmount)) {
													Log.customer.debug("%s::Ir Line Amount:%s",classname,ir_LineAmount);
													outPW_FlatFile.write(ir_LineAmount+"~|");
												}
												else {
													outPW_FlatFile.write("~|");
												}
												// Writing IL-Discount-Percent - 27
												outPW_FlatFile.write("00000.0000"+"~|");
												irLineCapsCharge = (ClusterRoot)IrLineItem2.getFieldValue("CapsChargeCode");
												// Writing IL-Charge-Type - 28
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
												// Writing IL-PO-No - 29
												if (!StringUtil.nullOrEmptyOrBlankString(irLinePoNumber)){
													Log.customer.debug("%s::IR Line PO Number:%s",classname,irLinePoNumber);
													outPW_FlatFile.write(irLinePoNumber+"~|");
												}
												else {
													outPW_FlatFile.write("~|");
												}
												// Writing IL-PO-Line-Seq-No - 30
												irLinePoLineNumber = IntegerFormatter.getIntValue(IrLineItem2.getFieldValue("POLineItemNumber"));
												irLinePoLinenoString = IntegerFormatter.getStringValue(irLinePoLineNumber);
												Log.customer.debug("%s::Line po Line number:%s",classname,irLinePoLinenoString);
												if (!StringUtil.nullOrEmptyOrBlankString(irLinePoLinenoString) && !irLinePoLinenoString.equals("0")){
													Log.customer.debug("%s::Ir Line Po Line Number:%s",classname,irLinePoLinenoString);
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
															String irMiscString  = null;
															String ilRecvFacilityCode = null;
															for (int j = 0; j<irSplitaccSize;j++){
																irSplitAccounting2 = (SplitAccounting)irSplitAccounting.get(0);
																irAccountingRecFacility = (String)IrLineItem2.getDottedFieldValue("ShipTo.ReceivingFacility");
																irAccountingFacility = (String)irSplitAccounting2.getFieldValue("AccountingFacility");
																irDepartment = (String)irSplitAccounting2.getFieldValue("Department");
																Log.customer.debug("%s::Accounting facility:%s",classname,irAccountingFacility);
																irOrder = (String)irSplitAccounting2.getFieldValue("Order");
																irDivision = (String)irSplitAccounting2.getFieldValue("Division");
																irSection = (String)irSplitAccounting2.getFieldValue("Section");
																irExpenseAccount = (String)irSplitAccounting2.getFieldValue("ExpenseAccount");
																irMisc = (String)irSplitAccounting2.getFieldValue("Misc");
																if(!StringUtil.nullOrEmptyOrBlankString(irAccountingFacility)){
																	Log.customer.debug("%s::Accounting facility:%s",classname,irAccountingFacility);
																	irAccFac = irAccountingFacility+"~|";
																	//outPW_FlatFile.write(irAccountingFacility+"~|");
																}
																else {
																	irAccFac = ("~|");
																	// outPW_FlatFile.write("~|"+"\n");
																}
																if(!StringUtil.nullOrEmptyOrBlankString(irAccountingRecFacility)){
																	ilRecvFacilityCode = irAccountingRecFacility+"~|";
																}
																else {
																	ilRecvFacilityCode = ("AA"+"~|");
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
																	irOrdername = irOrder+"~|";
																}
																else {
																	irOrdername =("~|");
																}
																if (irMisc!=null){
																	Log.customer.debug("%s::IR Misc:%s",classname,irMisc);
																	irMiscString  = irMisc+"~|";
																}
																else {
																	irMiscString  = ("~|");
																}
															  }
															   outPW_FlatFile.write("U9"+"~|");
															   outPW_FlatFile.write(ilRecvFacilityCode);
															   outPW_FlatFile.write(irAccFac);
															   outPW_FlatFile.write(irDepartment);
															   outPW_FlatFile.write(irDivisionString);
															   outPW_FlatFile.write(irSectionString);
															   outPW_FlatFile.write(irExpenseAccountString);
															   outPW_FlatFile.write(irOrdername);
															   outPW_FlatFile.write(irMiscString );
															   outPW_FlatFile.write("U9"+"~|");
															   irBuyerCode = (ClusterRoot)IrLineItem2.getFieldValue("BuyerCode");
															   	if (irBuyerCode!=null){
															   		irbuyercc = (String)irBuyerCode.getFieldValue("BuyerCode");
															   		if(!StringUtil.nullOrEmptyOrBlankString(irbuyercc)){
															   			Log.customer.debug("%s:: IR Buyer Code:%s",classname,irbuyercc);
															   			outPW_FlatFile.write(irbuyercc);
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
																				outPW_FlatFile.write(irPOlinebuyercc);
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
															outPW_FlatFile.write("U9"+"~|");
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
														outPW_FlatFile.write("U9"+"~|");
														//outPW_FlatFile.write("\n");
													}
												}
												else {
													SplitAccountingCollection irAccounting = IrLineItem2.getAccountings();
													if (irAccounting!=null){
														BaseVector irSplitAccounting = (BaseVector)irAccounting.getSplitAccountings();
														int irSplitaccSize = irSplitAccounting.size();
														if (irSplitaccSize > 0){
															String Ir_Facility_UniqueName = null;
															String ir_Fac = null;
															for (int k = 0; k<irSplitaccSize;k++){
																SplitAccounting irSplitAccounting2 = (SplitAccounting)irSplitAccounting.get(0);
																ClusterRoot   ir_Facility = (ClusterRoot)irSplitAccounting2.getFieldValue("Facility");
																Ir_Facility_UniqueName = (String)ir_Facility.getFieldValue("UniqeuName");
																if(!StringUtil.nullOrEmptyOrBlankString(Ir_Facility_UniqueName)){
																	Log.customer.debug("%s:: IR Facility:%s",classname,Ir_Facility_UniqueName);
																	ir_Fac = Ir_Facility_UniqueName+"~|";
																 }
																 else {
																	ir_Fac = ("~|");
																 }
															 }
															 outPW_FlatFile.write(ir_Fac);
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
														outPW_FlatFile.write("~|Catalog Item: ");
													}
													else {
														outPW_FlatFile.write("~|");
													Log.customer.debug("%s::isAdHocBoolean is true, not catalog item",classname);
													}
												}
												else {Log.customer.debug("%s::isAdHocBoolean is null, leave blank",classname);
													outPW_FlatFile.write(" ~|");
												}
												outPW_FlatFile.write("\n");
									}
									totalNumberOfIrWritten++;
//							        if(isCompletedFlgUpdate && totalNumberOfIrWritten==totalNumberOfIrs ) {
	                                if(isCompletedFlgUpdate) {
										//setInvoiceReconDWFlagCompleted();
								    	Log.customer.debug("%s::USIRFlagAction is Completed setting DWInvoiceFlag ...", classname);
										invrecon.setFieldValue("DWInvoiceFlag", "Processing");
							        }
							        else {
								    	Log.customer.debug("%s::USIRFlagAction is None no action req DWInvoiceFlag ...", classname);
								    	count++;
								    	if(count == 200)
									   {
											Log.customer.debug("**********Commiting IR Records*******  ",count);
											Base.getSession().transactionCommit();
											count = 0;
										}
										continue;
									}
									/* if(totalNumberOfIrWritten == 200)
									        {
												Log.customer.debug("**********Commiting IR Records*******  ",totalNumberOfIrWritten);
												Base.getSession().transactionCommit();
											    totalNumberOfIrWritten = 0;
											} */
								 }
							   }
							   catch(Exception e){
									Log.customer.debug(e.toString());
									new ScheduledTaskException("Error : " + e.toString(), e);
                                    throw new ScheduledTaskException("Error : " + e.toString(), e);
							   }
							   Log.customer.debug("Ending DWInvoicePush program .....");
							   count++;
							   if(count == 200)
							   {
							    	Log.customer.debug("**********Commiting IR Records*******  ",count);
									Base.getSession().transactionCommit();
									count = 0;
								}
							   }
							}
							Base.getSession().transactionCommit();
							Log.customer.debug("  Transaction Commited ********  ");
							String updateQuery = null;
							updateQuery = "update  ariba.invoicing.core.InvoiceReconciliation set DWInvoiceFlag = 'Completed' where DWInvoiceFlag = 'Processing'";
							Log.customer.debug("  update query ********  ",updateQuery);
							AQLUpdate aqlqueryUpdate = null;
							AQLOptions optionsUpdate = null;
							aqlqueryUpdate = AQLUpdate.parseUpdate("update  ariba.invoicing.core.InvoiceReconciliation set DWInvoiceFlag = 'Completed' where DWInvoiceFlag = 'Processing'");
							Log.customer.debug("  update query ********  ",aqlqueryUpdate);
							optionsUpdate = new AQLOptions(p);
							optionsUpdate.setSQLAccess(AQLOptions.AccessReadWrite);
							optionsUpdate.setClassAccess(AQLOptions.AccessReadWrite);
							int updateResults = Base.getService().executeUpdate(aqlqueryUpdate, optionsUpdate);
							Log.customer.debug("Number of records updated**************** " + updateResults);
							Base.getSession().transactionCommit();
                            // Ends here
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
						//Process runDWFTP = Runtime.getRuntime().exec("/usr/bin/sh /msc/arb821/Server/config/java/schedule/DWFileFTP.sh");
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
				message.append("CATUSDWInvoicePush_FlatFile Task Failed - Exception details below");
				message.append("\n");
				message.append(e.toString());
				mailSubject = "CATUSDWInvoicePush_FlatFile Task Failed";
				Log.customer.debug("%s: Inside Exception message "+ message.toString() , classname);
				new ScheduledTaskException("Error : " + e.toString(), e);
                throw new ScheduledTaskException("Error : " + e.toString(), e);
			 }
			  finally {
				outPW_FlatFile.flush();
				outPW_FlatFile.close();

				//Change made by Soumya begins
				Log.customer.debug("%s::Archive File Path:%s",classname,archiveFileDataPath);
				Log.customer.debug("CATUSDWInvoicePush_FlatFile:Starting Copying the flat file to Archive ");
				CATFaltFileUtil.copyFile(flatFilePath, archiveFileDataPath);
				Log.customer.debug("CATUSDWInvoicePush_FlatFile:Completed Copying the flat file to Archive ");

				try
				{
					Log.customer.debug("CATUSDWInvoicePush_FlatFile:Changing file permission of Data file.");
					Runtime.getRuntime().exec("chmod 666 " + flatFilePath);
					Log.customer.debug("CATUSDWInvoicePush_FlatFile:Changed file permission of Data file.");
				}catch (IOException e1) {
					Log.customer.debug("CATUSDWInvoicePush_FlatFile:Error in changing Permission. "+ e1);
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
	/*public void setInvoiceReconDWFlagCompleted(List irList){
		for (Iterator e = irList.iterator(); e.hasNext();){
			Log.customer.debug("%s:Inside the loop for mainking DWInvoiceFlag Completed::",classname);
			invrecon = (InvoiceReconciliation)e.next();
			Log.customer.debug("%s::IR object for which the DW Invoice Flag has to set to completed",classname,irobj);
			invrecon.setFieldValue("DWInvoiceFlag", "Completed");
		}
    } */
	
	//Start: Q4 2013 - RSD117 - FDD 2/TDD 2
	private String getFormatattedTxt(String inputTxt, int txtLength) {
		int fulllength = inputTxt.length(); // full length gives the length of IR number
		int rellength = txtLength + 3; //Inv Number + 'IR' + '-'
		int temp = fulllength - rellength;
		String formattedTxt = "";

		Log.customer.debug("CATUSDWInvoicePush_FlatFile: int temp  " + temp);

		inputTxt = inputTxt.substring(rellength, fulllength);


		formattedTxt = "IR-" + inputTxt;
		Log.customer.debug("CATUSDWInvoicePush_FlatFile: formattedTxt " + formattedTxt);
		return formattedTxt;

		}

	private String getFormatattedTxt(String inputTxt2) {

		inputTxt2 = inputTxt2.substring(0,33);
		Log.customer.debug("CATUSDWInvoicePush_FlatFile: inputTxt2 " + inputTxt2);
		return inputTxt2;

		}
	//End: Q4 2013 - RSD117 - FDD 2/TDD 2

	public CATUSDWInvoicePush_FlatFile(){
	}
}