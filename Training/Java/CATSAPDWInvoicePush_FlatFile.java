
/*******************************************************************************************************************************************
	Creator: Garima
	Description: Writing the feilds from the IR to flat file
	ChangeLog:
	Date		Name		     History
	15/05/09  Sudheer K Jain     Issue 958  Removing special charcter single quotes "'".
	02/11/10  PGS Kannan         Issue # 1054 / CR202  deafult irRecfacility is 02 if CompanyCode.SAPSource") is CBS 	then irRecfacility = "86
	29/11/11  IBM AMS Vikram Singh Filtering non ASCII characters
	15/06/2012 Dharshan   Issue #269	 IsAdHoc - catalog or non catalog,
    10/07/2013 IBM AMS Vikram Singh		 Q4 2013 - RSD117 - FDD 2/TDD 2 - Modify IR uniqueName when sending the IR number to PDW
	--------------------------------------------------------------------------------------------------------------

*******************************************************************************************************************************************/
package config.java.schedule.sap;
import java.io.File;
//change begin by Soumya
import java.io.IOException;
//change end by Soumya
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;

import ariba.approvable.core.LineItem;
import ariba.base.core.Base;
import ariba.base.core.BaseId;
import ariba.base.core.BaseVector;
import ariba.base.core.ClusterRoot;
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
import ariba.contract.core.Contract;
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
import ariba.util.formatter.BooleanFormatter;
import ariba.util.log.Log;
import ariba.util.scheduler.ScheduledTask;
import ariba.util.scheduler.ScheduledTaskException;
import ariba.util.scheduler.Scheduler;
//change made by Soumya begins
import config.java.schedule.util.CATFaltFileUtil;
//change made by Soumya ends

//TBD:::Whats needs to be filled if any of the field value is not present. right now writing it with ""
public class CATSAPDWInvoicePush_FlatFile extends ScheduledTask
{
	private Partition p;
	private String query;
	private String query1;
	private String controlid, policontrolid;
	private int count, lineitemcount;
	private double total, lineitemtotal;
    private ariba.util.core.Date datetimezone = null;
    private BaseId baseId = null;
    private String classname="CATSAPDWInvoicePush_FlatFile";
    private String flatFilePath = "/msc/arb9r1/downstream/catdata/DW/MSC_DW_INVOICE_PUSH_SAP.txt";
	//change made by soumya begings

	private String fileExtDateTime = "";
	private String archiveFileDataPath = "";

	//change made by soumya ends
    private String flatFilePathST;
    private String periodenddate = null;
    private String PeriodStartdate= null;
    private String partitionName= null;
    private boolean isHeader;
    private String IHBookdate = null;
    private BaseVector IrLineItem=null;
    private LineItemProductDescription IrLineDescritpiton = null;
    private String irLinePoNumber = null;
    private int partitionNumber;
    private int partition;
    private int totalNumberOfIrs;
    private int totalNumberOfIrWritten = 0;
    private FastStringBuffer message = null;
	private String mailSubject = null;
	private String startTime, endTime;
	private String USIRFlagAction = null;
	private boolean isCompletedFlgUpdate = false;
	private int lineCount,IrLineItemvector,IrnumberInCollection,irLinePoLineNumber,irSplitaccSize;
	private String irUniqueName = null;
	private String irRecfacility = null;
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
	private String irAccFac1= null;
	private String irrecFac1= null;
	private String irConAcc = null;
	private String irExpenseAccount = null;
	private String irMisc;
	private String ilRecvFacilityCode = null;
	private SplitAccounting irSplitAccounting2 = null;
	private String irAccountingFacility = null;
	private String irAccountingRecFacility = null;
	private String irAccountingBuyFacility = null;
	private String irSubAct = null;
	private String irSubSAct = null;
	private  ClusterRoot irBuyerCode = null;
	private String irbuyercc = null;
	private DirectOrder irLineOrder = null;
	private ariba.purchasing.core.POLineItem irOrderLineItem = null;
	private ClusterRoot irPOLineBuyerCode = null;
	private String irPOlinebuyercc = null;
	private ariba.invoicing.core.InvoiceReconciliation invrecon = null;
	private PrintWriter outPW_FlatFile = null;
    private String ExpenseAccount = null;
    private String irControlAccount = null;
    private String source = null;
	private Boolean isAdHoc;
	private boolean isAdHocBoolean;

	public void init(Scheduler scheduler, String scheduledTaskName, Map arguments) {
						super.init(scheduler, scheduledTaskName, arguments);
						for(Iterator e = arguments.keySet().iterator(); e.hasNext();)  {
							String key = (String)e.next();
							if (key.equals("queryST")) {
							Log.customer.debug("queryST");
								query  = (String)arguments.get(key);
						}
						else if(key.equals("flatFilePathST"))
							    	{
							    		flatFilePath = (String)arguments.get(key);
							    		Log.customer.debug("flatFilePathST "+flatFilePathST);
	    	}
			}
	 }

	public void run()
	throws ScheduledTaskException
	{
	Log.customer.debug("%s::Start of CATSAPDWInvoicePush_FlatFile task ...",classname);
	// Read the USPOFlagAction info from cat.dwAction.util
	USIRFlagAction = Fmt.Sil("cat.dwAction", "USIRFlagAction");
	Log.customer.debug("%s::USIRFlagAction ...", USIRFlagAction, classname);
    if (USIRFlagAction!=null && ! USIRFlagAction.equals("None") && USIRFlagAction.indexOf("not found")==-1)
		isCompletedFlgUpdate = false;
	if ( USIRFlagAction!=null && USIRFlagAction.equals("Completed")&& USIRFlagAction.indexOf("not found")==-1)
    	isCompletedFlgUpdate = true;
	startTime = DateFormatter.getStringValue(new ariba.util.core.Date(), "EEE MMM d hh:mm:ss a z yyyy", TimeZone.getTimeZone("CST"));
	periodenddate = DateFormatter.toYearMonthDate(Date.getNow());
	try {

			//change made by Soumya begins
			Date date = new Date();
			fileExtDateTime = CATFaltFileUtil.getFileExtDateTime(date);
			archiveFileDataPath = "/msc/arb9r1/downstream/catdata/DW/archive/MSC_DW_INVOICE_PUSH_SAP_ARCHIVE." + fileExtDateTime + ".txt";
			Log.customer.debug("%s::archiveFileDataPath:",classname);

			//change made by soumya ends

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
				//mailSubject = "CATSAPDWInvoicePush_FlatFile Task Completion Status - Completed Successfully";
				try
				{
					isHeader = false;
					//query = "select from ariba.invoicing.core.InvoiceReconciliation where DWInvoiceFlag = 'InProcess' or DWInvoiceFlag = 'Processing'";
					Log.customer.debug(query);
					AQLQuery aqlquery = null;
					AQLQuery aqlquery1 = null;
					AQLOptions options = null;
					AQLOptions options1 = null;
					AQLResultCollection results = null;
					AQLResultCollection results1 = null;
					aqlquery = AQLQuery.parseQuery(query);
					Log.customer.debug("aqlquery=>"+aqlquery);
					options = new AQLOptions(p);
					results = Base.getService().executeQuery(aqlquery, options);
					totalNumberOfIrs = results.getSize();
					if( (results != null) && (!results.isEmpty()))
								{
								while (results.next()) {
									query1=(String) results.getString(0);
									Log.customer.debug("query1 from staging table=>"+query1);
								}
			          }
			          // Parsing the staging query for receipt process
					  			aqlquery1 = AQLQuery.parseQuery(query1);
					  			Log.customer.debug("aqlquery1=>"+aqlquery1);
					  			options1 = new AQLOptions(p);
			                    results1 = Base.getService().executeQuery(aqlquery1, options1);

					//List irList = ListUtil.list();
					 int count = 0;
					 if( (results1 != null) && (!results1.isEmpty()))
			         {
					  while(results1.next()){
						invrecon = (InvoiceReconciliation)(results1.getBaseId("InvoiceReconciliation").get());
					    //irList.add(invrecon);
						if(invrecon != null){
						try{
								IrLineItem = (BaseVector)invrecon.getLineItems();
								Log.customer.debug("%s::Ir Line Item:%s",classname,IrLineItem);
								// Shaila : Issue 754 : To ensure no IR with zero line item is processed
								lineCount = invrecon.getLineItemsCount();


								// Added by Majid - starts


								int orderorMALineSize = 0;

								Contract masterAgreement = null;
								DirectOrder directOrder = null;
								String orderOrMasterAgreement = null;
								String orderMAbuyerCode = null;
								String orderMAbuyerUnqiueName = null;

								if(invrecon.getDottedFieldValue("Order") != null)
								{
									directOrder = (DirectOrder) invrecon.getDottedFieldValue("Order");
									Log.customer.debug("directOrder  =>" +directOrder);
									// orderOrMasterAgreement = "Order";
									// orderorMALineSize = directOrder.getLineItemsCount();
									orderMAbuyerUnqiueName = directOrder.getUniqueName();

									if(directOrder.getDottedFieldValue("LineItems[0].BuyerCode")!=null)
									{
									orderMAbuyerCode =  (String) directOrder.getDottedFieldValue("LineItems[0].BuyerCode.BuyerCode");
									Log.customer.debug("orderMAbuyerCode  =>" +orderMAbuyerCode);
									}
									else
									{
										orderMAbuyerCode = "";
										Log.customer.debug("orderMAbuyerCode  =>" +orderMAbuyerCode);
									}


								}
								else
								{
										//	Invoice is created against Contract directly
										masterAgreement =  (Contract) invrecon.getDottedFieldValue("MasterAgreement");
										Log.customer.debug("masterAgreement  =>" +masterAgreement);
										//orderOrMasterAgreement = "MasterAgreement";
										// orderorMALineSize = masterAgreement.getLineItemsCount();
										orderMAbuyerUnqiueName = masterAgreement.getUniqueName();
										if(masterAgreement.getDottedFieldValue("LineItems[0].BuyerCode")!=null)
										{
										orderMAbuyerCode =  (String) masterAgreement.getDottedFieldValue("LineItems[0].BuyerCode.BuyerCode");
										Log.customer.debug("orderMAbuyerCode  =>" +orderMAbuyerCode);
										}
										else
										{
											orderMAbuyerCode = "";
											Log.customer.debug("orderMAbuyerCode  =>" +orderMAbuyerCode);
										}


								}

								Log.customer.debug("orderorMALineSize =>" +orderorMALineSize);
								Log.customer.debug("orderOrMasterAgreement =>" +orderOrMasterAgreement);

								// Added by Majid - Ends


								Log.customer.debug("%s::Line Item count for IR:%s ",classname,lineCount);
								Log.customer.debug("%s::IR_period_end_date:%s",classname,periodenddate);
								if (partitionNumber==5)
								{
									//Date pSD = (Date)invrecon.getFieldValue("BlockStampDate");
									Date pSD = (Date)invrecon.getFieldValue("ControlDate");
									PeriodStartdate = DateFormatter.toYearMonthDate(pSD);
									Log.customer.debug("%s::Period Start Date:%s",pSD,classname);
									partitionName = "SAP";

									Date currentdate = new Date();
								}



								 if (lineCount > 0){

									// IHBookdate =  DateFormatter.toYearMonthDate(Date.getNow());

									// Commented to change the BookDate from Current Date to Paid date and if it is null then use current date
									 String PaidDate = null;
									 if(invrecon.getPaidDate()!=null)
									 {
									 Log.customer.debug("Paid Date is not null"+invrecon.getPaidDate());
									 PaidDate = DateFormatter.toYearMonthDate(invrecon.getPaidDate());
									 Log.customer.debug("Paid Date after formatting "+PaidDate);
									 }
									 else
									 {
										 Log.customer.debug("Paid Date is null");
										 // If Paid date is null then use current date
										 PaidDate =  DateFormatter.toYearMonthDate(Date.getNow());
										 Log.customer.debug("Paid Date after formatting "+PaidDate);
									 }
									 IHBookdate = PaidDate;
									 Log.customer.debug("%s::IHBookdate:%s",IHBookdate,classname);


									IrLineItemvector = IrLineItem.size();
									for (int i =0; i<IrLineItemvector;i++){
										LineItem irli = (LineItem)IrLineItem.get(i);
										//Writing partition name -1
										//String partition_name = String(partitionName);
										IrLineItem2 = (InvoiceReconciliationLineItem)IrLineItem.get(i);
										// Added to get the Buyer code from purchase Order
										String LineItemBuyerCode = "XX";

										// Added by Majid - starts

										if(directOrder != null )
										{
													Log.customer.debug("Buyer Code Path => Order.LineItems[0].Requisition.Requester.AccountingFacility");
													LineItemBuyerCode = (String)IrLineItem2.getDottedFieldValue("Order.LineItems[0].Requisition.Requester.AccountingFacility");
										}
										else
										{
											Log.customer.debug("Buyer Code Path => MasterAgreementRequest.Requester.AccountingFacility");
											LineItemBuyerCode = (String) masterAgreement.getDottedFieldValue("MasterAgreementRequest.Requester.AccountingFacility");
										}

										//	Added by Majid - Ends

										partitionName = "SAP";
										outPW_FlatFile.write(partitionName+"~|");
                                        Log.customer.debug("partitionName"+partitionName);

                                        //Writing Sapsource -2
                                        if (invrecon.getDottedFieldValue("CompanyCode.SAPSource") != null){
										source = (String)invrecon.getDottedFieldValue("CompanyCode.SAPSource");
//										 Comment Added by Majid - No need to write into file
										//outPW_FlatFile.write( source+ "~|");
										Log.customer.debug("%s::source:%s",classname,source);
										}
										else {
											// Comment Added by Majid - No need to write into file
											// outPW_FlatFile.write("~|");
											}

										//  irRecfacility = (String)IrLineItem2.getDottedFieldValue("Order.LineItems[0].Requisition.Requester.AccountingFacility");
										//	irRecfacility =  LineItemBuyerCode;
                                        // issue # 1054 / CR202  deafult is irRecfacility 02
                                        irRecfacility = "02";
                                        if (invrecon.getDottedFieldValue("CompanyCode.SAPSource") != null){
										         String source1 = (String)invrecon.getDottedFieldValue("CompanyCode.SAPSource");
                                        // if 	source getDottedFieldValue("CompanyCode.SAPSource") = CBS 	irRecfacility = "86";

									    if ( source1.equalsIgnoreCase("CBS") ){
											irRecfacility = "86";

									    }
									   }

										 Log.customer.debug("irRecfacility"+irRecfacility);
										//Writing record facility-3
										if(irRecfacility != null){
										outPW_FlatFile.write(irRecfacility+"~|");
										Log.customer.debug("irRecfacility"+irRecfacility);
										}
										else {
											outPW_FlatFile.write("~|");
										 }


										irUniqueName = (String)invrecon.getFieldValue("UniqueName");
										Log.customer.debug("%s::IRUniqueName: %s",classname,irUniqueName);

										// Writing IH-Invoice-No-4
										//Start: Q4 2013 - RSD117 - FDD 2/TDD 2
										//outPW_FlatFile.write(irUniqueName+"~|");										
										
										String SupInvNumber = (String)invrecon.getDottedFieldValue("Invoice.InvoiceNumber");
										Log.customer.debug("CATSAPDWInvoicePush_FlatFile: SupInvNumber: "+ SupInvNumber);
										int invleng = SupInvNumber.length();
										if (invleng > 33)
										{	
											String SupInvNumber1 = getFormatattedTxt(SupInvNumber);
											int invleng1 = SupInvNumber1.length();
											Log.customer.debug("CATSAPDWInvoicePush_FlatFile: invleng1: "+ invleng1);
											String FormattedirUniqueName = getFormatattedTxt(irUniqueName, invleng1);
											Log.customer.debug("CATSAPDWInvoicePush_FlatFile: FormattedirUniqueName: "+ FormattedirUniqueName);

											// Writing IH-Invoice-No - 4
											outPW_FlatFile.write(FormattedirUniqueName+"~|");

										}
										else
										{
											String FormattedirUniqueName = getFormatattedTxt(irUniqueName, invleng);
											Log.customer.debug("CATSAPDWInvoicePush_FlatFile: FormattedirUniqueName: "+ FormattedirUniqueName);

											// Writing IH-Invoice-No - 4
											outPW_FlatFile.write(FormattedirUniqueName+"~|");

										}
										//End: Q4 2013 - RSD117 - FDD 2/TDD 2
										irSupplierLocation = (String)invrecon.getDottedFieldValue("SupplierLocation.UniqueName");
										if (irSupplierLocation != null){
											Log.customer.debug("%s::IR_SupplierLocation:%s",classname,irSupplierLocation);
											// Writing IH-Supplier-Code- 5

											String formattedSuppLocCode = null;
											if(irSupplierLocation.length()>1)
											{
												// To truncate string like VN,PI,GS or OA etc whcih is always for Suppliers for SAP partiion
												String last2Char = irSupplierLocation.substring((irSupplierLocation.length()-2),irSupplierLocation.length());
												Log.customer.debug("%s::last2Char :%s",classname,last2Char);
												if(last2Char.equals("VN") ||last2Char.equals("PI") || last2Char.equals("GS") || last2Char.equals("OA") )
												{
												Log.customer.debug("%s::Inside Truncation section :%s",classname,last2Char);
												formattedSuppLocCode = irSupplierLocation.substring(0,(irSupplierLocation.length()-2));
												}
												else
												{
													Log.customer.debug("%s::OutSide Truncation section :%s",classname,last2Char);
													formattedSuppLocCode = irSupplierLocation;
												}

												Log.customer.debug("%s::supplierCode after truncation:%s",classname,formattedSuppLocCode);
											}
											else
											{
												// If length of SuppLocation id is less than 2
												formattedSuppLocCode = irSupplierLocation;
												Log.customer.debug("%s::supplierCode without truncation :%s",classname,formattedSuppLocCode);
											}
											outPW_FlatFile.write(formattedSuppLocCode + "~|");
											}
										else{
											outPW_FlatFile.write("~|");
										}
										Date Ir_invDate = (Date)invrecon.getFieldValue("InvoiceDate");
										if (Ir_invDate!=null){
											irinvdateyymmdd = DateFormatter.toYearMonthDate(Ir_invDate);
											Log.customer.debug("%s::Invoice Date:%s",classname,irinvdateyymmdd);
											// Writing IH-Invoice-Date-6
											outPW_FlatFile.write(irinvdateyymmdd+"~|");
										}
										else{
											// If Invoice Date is null then use the Book date which is Paid date
											Log.customer.debug("Invoice date is null");
											outPW_FlatFile.write(IHBookdate+"~|");
										}

										irTotalcost = (Money)invrecon.getFieldValue("TotalCost");
										if (irTotalcost!=null){
											Irtotalcostamount = (BigDecimal)invrecon.getDottedFieldValue("TotalCost.Amount");
											Log.customer.debug("%s::Total cost amount:%s",classname,Irtotalcostamount);
											if (Irtotalcostamount!=null) {
												//Writing IH-Invoice-Type-7
												int Ir_tca = Irtotalcostamount.intValue();
												if (Ir_tca >= 0) {
													outPW_FlatFile.write("01"+"~|");
												}
												else if (Ir_tca < 0){
													outPW_FlatFile.write("02"+"~|");
												}
												// Writing IH-Book-Date/ControlDate-8

												// Need to verify for Control Date -- Majid

												outPW_FlatFile.write(IHBookdate+"~|");
												Log.customer.debug("IHBookdate");

												irTotalCostCurrency = irTotalcost.getCurrency().getUniqueName();
												if (!StringUtil.nullOrEmptyOrBlankString(irTotalCostCurrency)){
													//Writing IH-Currency-Code-9
													Log.customer.debug("%s::TotalCost Currency:%s",classname,irTotalCostCurrency);
													outPW_FlatFile.write(irTotalCostCurrency+"~|");
												}
												else {
													outPW_FlatFile.write("~|");
												}
												irStngTotalCost = BigDecimalFormatter.getStringValue(Irtotalcostamount);
												Log.customer.debug("%s::Writing IR total cost:%s",classname,irStngTotalCost);
												// Writin IH-Total-Invoice-Amount-10
												outPW_FlatFile.write(irStngTotalCost+"~|");
											}
											else {
												outPW_FlatFile.write("~|");

												//	Need to verify for Control Date -- Majid
											    outPW_FlatFile.write(IHBookdate+"~|");
												outPW_FlatFile.write("~|");
												outPW_FlatFile.write("~|");
										   }
										}
										else {
											outPW_FlatFile.write("~|");
											//	Need to verify for Control Date -- Majid
											outPW_FlatFile.write(IHBookdate+"~|");
											outPW_FlatFile.write("~|");
											outPW_FlatFile.write("~|");
										}

										irTotalInvoiceDiscountDollarAmount = (BigDecimal)invrecon.getDottedFieldValue("TotalInvoiceDiscountDollarAmount.Amount");
										Log.customer.debug("%s::TotalInvoiceDiscountDollarAmount:%s",classname,irTotalInvoiceDiscountDollarAmount);
										//Writing Discount Amount-11
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

											//IrLineItem2 = (InvoiceReconciliationLineItem)IrLineItem.get(i);
											IrnumberInCollection = IrLineItem2.getNumberInCollection();
											numberInCollection = IntegerFormatter.getStringValue(IrnumberInCollection);
											Log.customer.debug("%s::IR number in collection:%s",classname,numberInCollection);
                                            //Writing Seq no-12
											outPW_FlatFile.write(numberInCollection+"~|");

											IrLineDescritpiton = IrLineItem2.getDescription();

											if (IrLineDescritpiton!=null){
												Log.customer.debug("%s::IR_LineDescription:%s",classname,IrLineDescritpiton);
												irDescItemNumber = IrLineDescritpiton.getItemNumber(); //what if this is blanck, right now considering blanck also as null
												Log.customer.debug("%s::Writing IR_LineDescription Item Number:%s",classname,irDescItemNumber);
												//Writing desc item no and class code-13,14
												if (!StringUtil.nullOrEmptyOrBlankString(irDescItemNumber)){
													outPW_FlatFile.write(irDescItemNumber+"~|");
													outPW_FlatFile.write("K"+"~|");
												}
												else {
													outPW_FlatFile.write("~|");
													outPW_FlatFile.write("0"+"~|");
												}
												//Writing item desc-15
												// Filtering Non-ASCII characters
												String descDescription = "";
												descDescription_temp = (String)IrLineDescritpiton.getFieldValue("Description");
												descDescription = descDescription_temp.replaceAll("[^\\p{ASCII}]", "");
												if (!StringUtil.nullOrEmptyOrBlankString(descDescription)){
													descDescription = StringUtil.replaceCharByChar(descDescription,'\r',' ');
													descDescription = StringUtil.replaceCharByChar(descDescription,'\t',' ');
													descDescription = StringUtil.replaceCharByChar(descDescription,'\n',' ');
													descDescription = StringUtil.replaceCharByChar(descDescription,'\'',' ');
													Log.customer.debug("%s::Description:%s",classname,descDescription);
													outPW_FlatFile.write(descDescription+"~|");
												}
												else{
													outPW_FlatFile.write("~|");
												}
												//Writing UNSPSC code-16
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

												//Writing supplier item no-17
												descSupplierPartNumber = IrLineDescritpiton.getSupplierPartNumber();
												Log.customer.debug("%s::Description_SupplierPartNumber:%s",classname,descSupplierPartNumber);
												if(!StringUtil.nullOrEmptyOrBlankString(descSupplierPartNumber)){
													Log.customer.debug("%s::Writing Description_SupplierPartNumber:%s",classname,descSupplierPartNumber);
													outPW_FlatFile.write(descSupplierPartNumber+"~|");
													Log.customer.debug("descSupplierPartNumber"+descSupplierPartNumber);
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
												outPW_FlatFile.write("~|");
												outPW_FlatFile.write("~|");
											 }

											 IrLineType = IrLineItem2.getLineType();
											 // Writing IL-Line-Type-18
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

											   // Writing hazardous item-19
											 Boolean isHaz = (Boolean)IrLineItem2.getFieldValue("IsHazmat");
											// irHazmat = BooleanFormatter.getStringValue(IrLineItem2.getFieldValue("IsHazmat"));
											if(isHaz != null)
											{
											 boolean irHazmatBoolean = isHaz.booleanValue();
											Log.customer.debug("Ir Hazmat value:");
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

											 IrLineQuantity = BigDecimalFormatter.getStringValue(IrLineItem2.getQuantity());
											 // Writing IL-Bill-Qty-20
											 if (!StringUtil.nullOrEmptyOrBlankString(IrLineQuantity)){
												Log.customer.debug("%s::IR LineQuantity:%s",classname,IrLineQuantity);
												outPW_FlatFile.write(IrLineQuantity+"~|");
											 }
											 else {
												outPW_FlatFile.write("~|");
											 }
											 // Writing IL-Bill-Qty-Unit-Of-Measure-21
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
											 // Writing IL-Currency-Code-22
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
											 // Writing IL-Unit-Price - 23
											 // Added a abs() method to always pass the Positive Unit Price
											 Log.customer.debug("Unit Price without abs method "+ BigDecimalFormatter.getStringValue(IrLineDescritpiton.getPrice().getAmount()));
											 Log.customer.debug("Unit Price with abs method "+ BigDecimalFormatter.getStringValue(IrLineDescritpiton.getPrice().getAmount().abs()));
											 IrdecsPrice =  BigDecimalFormatter.getStringValue(IrLineDescritpiton.getPrice().getAmount().abs());
											 Log.customer.debug("%s::Ir desc Price:%s",classname,IrdecsPrice);
											 if (!StringUtil.nullOrEmptyOrBlankString(IrdecsPrice)){
												outPW_FlatFile.write(IrdecsPrice+"~|");
											 }
											 else {
												outPW_FlatFile.write("~|");
											 }
											 // Writing IL-Unit-Price-Unit of Measure-24
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
												// Writing IL-Unit-Price-Qty-Conversion-Factor-25
												outPW_FlatFile.write("1.0"+"~|");
												//Writing IL-Extended-Price-26
												ir_LineAmount = BigDecimalFormatter.getStringValue(IrLineItem2.getAmount().getAmount());
												if (!StringUtil.nullOrEmptyOrBlankString(ir_LineAmount)) {
													Log.customer.debug("%s::Ir Line Amount:%s",classname,ir_LineAmount);
													outPW_FlatFile.write(ir_LineAmount+"~|");
												}
												else {
													outPW_FlatFile.write("~|");
												}
												// Writing IL-Discount-Percent-27
												outPW_FlatFile.write("00000.0000"+"~|");
												irLineCapsCharge = (ClusterRoot)IrLineItem2.getFieldValue("CapsChargeCode");
												// Writing IL-Charge-Type-25
												if (irLineCapsCharge!=null){
													Log.customer.debug("%s::Ir Line caps charge code:%s",classname,irLineCapsCharge);
													irCapsUniqueName = (String)irLineCapsCharge.getFieldValue("UniqueName");
													Log.customer.debug("%s::Ir Line caps charge code UniqueName:%s",classname,irCapsUniqueName);
													outPW_FlatFile.write(irCapsUniqueName+"~|");
												}
												else {
													outPW_FlatFile.write("~|");
												}


												// irLinePoNumber = (String)invrecon.getFieldValue("PONumber");
												irLinePoNumber = orderMAbuyerUnqiueName;

												// Writing IL-PO-No-28
												if (!StringUtil.nullOrEmptyOrBlankString(irLinePoNumber)){
													Log.customer.debug("%s::IR Line PO Number:%s",classname,irLinePoNumber);
													outPW_FlatFile.write(irLinePoNumber+"~|");
												}
												else {
													outPW_FlatFile.write("~|");
												}
												// Writing IL-PO-Line-Seq-No-29
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
												//Writing IL- Spend-Category-30
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
												int partitionNumber_5 = invrecon.getPartitionNumber();
												Log.customer.debug("%s::partiton number 5th place:%s",classname,partitionNumber_5);
												if (partitionNumber_5==5){
													irAccounting = IrLineItem2.getAccountings();
													if (irAccounting!=null){
														irSplitAccounting = (BaseVector)irAccounting.getSplitAccountings();
														irSplitaccSize = irSplitAccounting.size();
														Log.customer.debug("%s::Split acc size:%s",classname,irSplitaccSize);
														//if (irSplitaccSize > 0){
															Log.customer.debug ("%s::getting accounting facility",classname);

																String ilRecvFacilityCode = (String)IrLineItem2.getDottedFieldValue("ShipTo.ReceivingFacility");
																Log.customer.debug("%s::Accounting facility:%s",classname,ilRecvFacilityCode);

																irSplitAccounting2 = (SplitAccounting)irSplitAccounting.get(0);
																// irAccountingBuyFacility = (String)IrLineItem2.getDottedFieldValue("Order.LineItems[0].Requisition.Requester.AccountingFacility");
																irAccountingBuyFacility = LineItemBuyerCode;
															    irControlAccount = (String)invrecon.getDottedFieldValue("CompanyCode.UniqueName");
																Log.customer.debug("%s::Accounting facility:%s",classname,irAccountingFacility);
																irSubAct= (String)IrLineItem2.getDottedFieldValue("ShipTo.UniqueName");
																Log.customer.debug("irSubAct"+irSubAct);
																irSubSAct = (String)irSplitAccounting2.getFieldValue("CostCenterText");
																irExpenseAccount = (String)irSplitAccounting2.getFieldValue("GeneralLedgerText");

																//irMisc = (String)irSplitAccounting2.getFieldValue("Misc");
																if(!StringUtil.nullOrEmptyOrBlankString(irAccountingBuyFacility)){
																	Log.customer.debug("%s::Accounting facility:%s",classname,irAccountingFacility);
																	irAccFac = irAccountingBuyFacility+"~|";
																	//outPW_FlatFile.write(irAccountingFacility+"~|");
																}
																else {
																	irAccFac = ("~|");
																	// outPW_FlatFile.write("~|"+"\n");
																}
																if(!StringUtil.nullOrEmptyOrBlankString(ilRecvFacilityCode)){
																	irrecFac1 = ilRecvFacilityCode+"~|";


																}
																else {
																	irrecFac1 = ("~|");


																}
																if(!StringUtil.nullOrEmptyOrBlankString(irAccountingBuyFacility)) {
																	irAccFac1 = irAccountingBuyFacility+"~|";
																}
																else {
																	irAccFac1 = ("~|");
																}
																if(!StringUtil.nullOrEmptyOrBlankString(irControlAccount)) {
																	irConAcc = irControlAccount+"~|";
																}
																else {
																	irConAcc = ("~|");
																}
																if (irSubAct!=null){
																	Log.customer.debug("%s::Sub Act:%s",classname,irSubAct);
																	irSubAct = irSubAct+"~|";
																}
																else {
																	irSubAct = ("~|");
																}
																if(irSubSAct!=null){
																	Log.customer.debug("%s::irSubSAct:%s",classname,irSubSAct);
																	irSubSAct = irSubSAct+"~|";
																}
																else {
																	irSubSAct = ("~|");
																}
																if (irExpenseAccount!=null){
																	Log.customer.debug("%s::IR Expense Account:%s",classname,irExpenseAccount);
																	irExpenseAccount  = irExpenseAccount+"~|";
																}
																else {
																	irExpenseAccount = ("~|");
																}


															   //Writing buy facility-49
															   outPW_FlatFile.write(irAccFac);
															   //Writing receive facility-50
															   outPW_FlatFile.write(irrecFac1);
															   Log.customer.debug("irrecFac1");
															   //Writing account facility-52
															   outPW_FlatFile.write(irAccFac1);
															   Log.customer.debug("irAccFac");
															   //Writing Control Acct-53
															   outPW_FlatFile.write(irConAcc);
															   Log.customer.debug("ConAcc");
															   //writing Sub Act-54
															   outPW_FlatFile.write(irSubAct);
															   Log.customer.debug("irSubAct");
															   //Writing SubSAct-55
															   outPW_FlatFile.write(irSubSAct);
															   Log.customer.debug("irSubSAct");
															   //Writing ExpenseAct-56
															   outPW_FlatFile.write(irExpenseAccount);
															   Log.customer.debug("irExpenseAccount");

															   //Writing OrderNo-57
															   //outPW_FlatFile.write("~|");
															   //Log.customer.debug("irOrder");

                                                               if ( source != null ) {
															   if(source.equals("MACH1"))
																{
																	Log.customer.debug("DW PO Push : Inside Mach1 "+source);
																if (irSplitAccounting2.getFieldValue("WBSElementText") != null)
																	{
																	String irOrder = irSplitAccounting2.getFieldValue("WBSElementText").toString();
																	if(irOrder.length()>10)
																	{
																		Log.customer.debug("DW PO Push : irOrder has more than 10 chars: Needs to trucnate "+irOrder);
																		outPW_FlatFile.write(irOrder.substring(0,10) + "~|");
																	}
																	else
																	{
																		outPW_FlatFile.write(irOrder + "~|");
																	}
																	Log.customer.debug("%s::irOrder:%s",classname,irOrder);
																	}
																	else
																	{
																		Log.customer.debug("DW PO Push : Inside Mach1 : WBSElementText is null ");
																		outPW_FlatFile.write("~|");
																	}

																}
																else if(source.equals("CBS"))
																{
																	Log.customer.debug("DW PO Push : Inside CBS "+source);
																	if (irSplitAccounting2.getFieldValue("InternalOrderText") != null)
																	{
																	String irOrder = irSplitAccounting2.getFieldValue("InternalOrderText").toString();
																	if(irOrder.length()>10)
																	{
																		Log.customer.debug("DW PO Push : irOrder has more than 10 chars: Needs to trucnate "+irOrder);
																		outPW_FlatFile.write(irOrder.substring(0,10) + "~|");
																	}
																	else
																	{
																		outPW_FlatFile.write(irOrder + "~|");
																	}
																	Log.customer.debug("%s::irOrder:%s",classname,irOrder);
																	}
																	else
																	{
																		Log.customer.debug("DW PO Push : Inside Mach1 : IO is null ");
																		outPW_FlatFile.write("~|");
																	}
																}

																else
																{
																	Log.customer.debug("DW PO Push : Invalid SAP Source "+source);
																	outPW_FlatFile.write("~|");
																}
															}


															   //Writing Misc-58
															   outPW_FlatFile.write("~|");
															   Log.customer.debug("irMisc");
															   String irAccFac3 = "R8"; // default value
																   Log.customer.debug("irAccFac3" + irAccFac3);
															   //Writing prodbuy Fac code-62
															   // Added by Majid to include the AccountingFacility
																if(!StringUtil.nullOrEmptyOrBlankString(irAccountingBuyFacility)) {
																	irAccFac3 = irAccountingBuyFacility + "~|";
																	Log.customer.debug("After assigning Facility Value irAccFac3 =>" + irAccFac3);
																}
																else {
																	Log.customer.debug("After assigning Facility Value to irAccFac3 when Facilit is null =>" + irAccFac3);
																		irAccFac3 = "~|";
																}

															   outPW_FlatFile.write(irAccFac3);

															   Log.customer.debug("ProdBuyFacCode");



															   String irBuyercode = null;
															   if(IrLineItem2.getDottedFieldValue("BuyerCode")!=null)
															   {
															   String irBuyercodetemp = (String)IrLineItem2.getDottedFieldValue("BuyerCode.BuyerCode") ;
															   irBuyercode = irBuyercodetemp + "~|";
															   Log.customer.debug("After assigning BuyerCode Value irBuyercode =>" + irBuyercode);
															   }
															   else {
																	Log.customer.debug("After assigning Order or MA BuyerCode Value to irBuyercode" + irBuyercode);

																	if(orderMAbuyerCode != null)
																	{
																	irBuyercode = orderMAbuyerCode + "~|";
																	}else
																	{
																		irBuyercode = "~|";
																	}
																	Log.customer.debug("After assigning Order or MA BuyerCode Value to irBuyercode" + irBuyercode);
																}


															   //writing ProdBuy code-63

															   outPW_FlatFile.write(irBuyercode);
															   Log.customer.debug("irBuyercode");

																//IsAdHoc - catalog or non catalog, Issue #269 - Dharshan
																isAdHocBoolean = true;
																isAdHoc = null;
																if (irli.getDottedFieldValue("IsAdHoc") != null) {
																	isAdHoc = (Boolean) irli.getDottedFieldValue("IsAdHoc");
																	isAdHocBoolean = BooleanFormatter.getBooleanValue(isAdHoc);
																	Log.customer.debug("%s::isAdHocBoolean:%s",classname,isAdHocBoolean);
																	if(isAdHocBoolean == false){
																		outPW_FlatFile.write("Catalog Item:");
																	}
																	else {
																	Log.customer.debug("%s::isAdHocBoolean is true, not catalog item",classname);
																	}
																}
																else {Log.customer.debug("%s::isAdHocBoolean is null, leave blank",classname);
																}
															   outPW_FlatFile.write("\n");

													}

												}

									}
									totalNumberOfIrWritten++;
							        //if(isCompletedFlgUpdate && totalNumberOfIrWritten==totalNumberOfIrs ) {
										if(isCompletedFlgUpdate) {
										//setInvoiceReconDWFlagCompleted();
										Log.customer.debug("isCompletedFlgUpdate" +isCompletedFlgUpdate);
										Log.customer.debug("totalNumberOfIrWritten" +totalNumberOfIrWritten);
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
									 if(totalNumberOfIrWritten == 200)
									        {
												Log.customer.debug("**********Commiting IR Records*******  ",totalNumberOfIrWritten);
												Base.getSession().transactionCommit();
											    totalNumberOfIrWritten = 0;
											}
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
							Log.customer.debug("  update query ********  " +updateQuery);
							AQLUpdate aqlqueryUpdate = null;
							AQLOptions optionsUpdate = null;
							aqlqueryUpdate = AQLUpdate.parseUpdate("update  ariba.invoicing.core.InvoiceReconciliation set DWInvoiceFlag = 'Completed' where DWInvoiceFlag = 'Processing'");
							Log.customer.debug("  update query ********  " +aqlqueryUpdate);
							optionsUpdate = new AQLOptions(p);
							optionsUpdate.setSQLAccess(AQLOptions.AccessReadWrite);
							optionsUpdate.setClassAccess(AQLOptions.AccessReadWrite);
							int updateResults = Base.getService().executeUpdate(aqlqueryUpdate, optionsUpdate);
							Log.customer.debug("Number of records updated**************** " + updateResults);
							Base.getSession().transactionCommit();
                            // Ends here
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
				message.append("CATSAPDWInvoicePush_FlatFile Task Failed - Exception details below");
				message.append("\n");
				message.append(e.toString());
				//mailSubject = "CATSAPDWInvoicePush_FlatFile Task Failed";
				Log.customer.debug("%s: Inside Exception message "+ message.toString() , classname);
				new ScheduledTaskException("Error : " + e.toString(), e);
                throw new ScheduledTaskException("Error : " + e.toString(), e);
			 }
			  finally {
				outPW_FlatFile.flush();
				outPW_FlatFile.close();

				//Change made by Soumya begins

				Log.customer.debug("CATSAPDWInvoicePush_FlatFile:Starting Copying the flat file to Archive ");
				CATFaltFileUtil.copyFile(flatFilePath, archiveFileDataPath);
				Log.customer.debug("CATSAPDWInvoicePush_FlatFile:Completed Copying the flat file to Archive ");

				try
				{
					Log.customer.debug("CATSAPDWInvoicePush_FlatFile:Changing file permission of Data file.");
					Runtime.getRuntime().exec("chmod 666 " + flatFilePath);
					Log.customer.debug("CATSAPDWInvoicePush_FlatFile:Changed file permission of Data file.");
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
			 	//CatEmailNotificationUtil.sendEmailNotification(mailSubject, message.toString(), "cat.java.emails", "DWPushNotify");
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

		Log.customer.debug("CATSAPDWInvoicePush_FlatFile: int temp  " + temp);

		inputTxt = inputTxt.substring(rellength, fulllength);


		formattedTxt = "IRS-" + inputTxt;
		Log.customer.debug("CATSAPDWInvoicePush_FlatFile: formattedTxt " + formattedTxt);
		return formattedTxt;

		}

	private String getFormatattedTxt(String inputTxt2) {

		inputTxt2 = inputTxt2.substring(0,33);
		Log.customer.debug("CATSAPDWInvoicePush_FlatFile: inputTxt2 " + inputTxt2);
		return inputTxt2;

		}
	//End: Q4 2013 - RSD117 - FDD 2/TDD 2

	public CATSAPDWInvoicePush_FlatFile(){
	}
}