// Created by KS for R1
// Updated by KS for R4 on Oct 11, 2005 - added helper method for approval rules
//updated by Aswini for PCL logic and passing Plant and Buyer Assgn in isCategoryManagerRequired and getCategoryManager methods and modified IP rule to include custom shipTo
//updated by Divya to add Tax Manager based on companycode for Vertex getRoleforSplitterRuleForVertex
//Mounika.k  17-04-2012 WI-275  Modified code to include plant code level roles for Hazmat,Indirect purchasing,Central Receiving,Block Admin,Tax Manager,Capital IT approver
//Naresh B 14-05-2012 WI-285 Modified code to add null check condition for CustomShipTo field. Some times CustomShipTo field is null if any requester not submitted the user profile.
//Jyoti K 14-06-2012 WI-87 Added a new method Role getRoleforSplitterRuleforInvoice to  inculde companycode specific Invoice Manager role for invoices.
/*255-IBM AMS_Bijesh Kumar-Budget Check Logic for Account Type "F" and Company Code '1000'*/
//182-IBM AMS_Vikram - Add Requisitioner's supervisor as the approver for Singapore credit invoices
// 08/21/2013		Jayashree B S	     		Q4 2013 - RSD103 - FDD 4/TDD 1.1	         Add CSCL Customs Team into PR and IR Approval Flow


package config.java.common;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import ariba.procure.core.ProcureLineItemCollection;
import ariba.approvable.core.Approvable;
import ariba.approvable.core.ApprovalRequest;
import ariba.base.core.Base;
import ariba.base.core.BaseObject;
import ariba.base.core.BaseVector;
import ariba.base.core.ClusterRoot;
import ariba.base.core.Partition;
import ariba.basic.core.Currency;
import ariba.basic.core.Money;
import ariba.common.core.Accounting;
import ariba.common.core.UserProfile;
import ariba.contract.core.ContractRequestLineItem;
import ariba.procure.core.ProcureLineItem;
import ariba.purchasing.core.ReqLineItem;
import ariba.receiving.CSVCommodityCodeMapReader;
import ariba.receiving.core.Receipt;
import ariba.user.core.Role;
import ariba.util.core.ListUtil;
import ariba.util.core.StringUtil;
import ariba.util.log.Log;
import ariba.invoicing.core.InvoiceReconciliationLineItem;

/*
 * AUL : Changed the call of constuctor for CSVCommodityCodeMapReader
 */
public class CatCommonUtil {

	private static final String ClassName = "CatCommonUtil";
	public static final String COMPANYCODE_FileName = "config/variants/SAP/data/mach1CompanyCode.csv";
	public static final String ACCOUNTTYPE_FileName = "config/variants/SAP/data/SAPAccountType.csv";
	//WI 182: Vikram added below 1 line (to be referenced from Invoice Manager and Credit Supervisor simple rules)
	public static final String CC_FileName = "config/variants/SAP/data/creditManager.csv";

    /**
        S. Sato - AUL - Parameters to disable/enable integration
    */
    public static final String DisableIntegrationParam =
        "Application.Caterpillar.DisableIntegration";

    public static final boolean DisableIntegration =
            Base.getService().getBooleanParameter(
                    Partition.getNone(),
                    DisableIntegrationParam);

	public CatCommonUtil() {
		super();
	}

//  *** Returns BaseVector of all approval requests for the approvable ***
	public static BaseVector getAllApprovalRequests(Approvable approvable) {
		BaseVector allrequests = new BaseVector();
		BaseVector requests = approvable.getApprovalRequests();
		while (!requests.isEmpty()) {
			Log.customer.debug("CatCommonUtil *** requests.size: " + requests.size());
			BaseVector newdepends = new BaseVector();
			for (Iterator itr1 = requests.iterator(); itr1.hasNext();) {
				ApprovalRequest ar = (ApprovalRequest)itr1.next();
//				Log.customer.debug("CatCommonUtil *** ar reason(outer): " + ar.getReason());
				ListUtil.addElementIfAbsent(allrequests, ar);
				Log.customer.debug("CatCommonUtil *** ALLrequests.size (1): " + allrequests.size());
				BaseVector depends = ar.getDependencies();
				Log.customer.debug("CatCommonUtil *** depends.size: " + depends.size());
				for (Iterator itr2 = depends.iterator(); itr2.hasNext();) {
					ar = (ApprovalRequest)itr2.next();
//					Log.customer.debug("CatCommonUtil *** ar reason(inner): " + ar.getReason());
					ListUtil.addElementIfAbsent(allrequests, ar);
					Log.customer.debug("CatCommonUtil *** ALLrequests.size (2): " + allrequests.size());
				}
				newdepends.addAll(depends);
//				Log.customer.debug("CatCommonUtil *** NEWdepends.size (2): " + newdepends.size());
			}
			requests = newdepends;
		}
		return allrequests;
	}

//  *** Returns User's default department accounting (Dept + Div + Sect) ***
	public static String getUserDepartmentAcctng(ariba.user.core.User user, Partition part) {
		String deptacct = null;
		if (user != null) {
			ariba.common.core.User partuser = ariba.common.core.User.getPartitionedUser(user, part);
			if (partuser != null) {
				Accounting uacct = partuser.getAccounting();
				String udept = (String)uacct.getFieldValue("Department");
				String udiv = (String)uacct.getFieldValue("Division");
				String usect = (String)uacct.getFieldValue("Section");
				if (udept != null && udiv != null && usect != null) {
					StringBuffer sb = new StringBuffer();
				    sb.append(udept.toUpperCase()).append(udiv.toUpperCase()).append(usect.toUpperCase());
					deptacct = sb.toString();
				}
			}
		}
		return deptacct;
	}

//	*** Returns List of values parsed from a String ("," is the token) ***
 	public static List parseParamString (String paramString)  	{
	     List paramList = new ArrayList();
	     StringTokenizer stk = new StringTokenizer(paramString, ",");
	     while (stk.hasMoreTokens())
	     {
	     	paramList.add(stk.nextToken(","));
	     }
	     return paramList;
	}

//	*** Returns hash value for key if found in file corresponding to filename (BufferedReader version) ***
 	public static String getHashValueFromFile (String key, String filename) {

 		String value = null;
 		if (!StringUtil.nullOrEmptyOrBlankString(filename) && key != null) {
 			File file = new File(filename);
 //			Log.customer.debug("%s *** file: %s", ClassName, file);
 			if (file != null) {
 				try {
 					BufferedReader br = new BufferedReader(new FileReader(file));
 		 			Log.customer.debug("%s *** br: %s", ClassName, br);
 		 			String line = null;
 		 			HashMap map = new HashMap();
 					while ((line = br.readLine())!= null) {
// 						Log.customer.debug("%s *** line: %s", classname, line);
						List values = parseParamString(line);
// 			 			Log.customer.debug("%s *** values: %s", ClassName, values);
//	 			 		Log.customer.debug("CatCommonUtil *** values.size = " + values.size());
 						if (values.size() > 1)
	 			 			map.put(values.get(0), values.get(1));
 					}
 					Log.customer.debug("CatCommonUtil2 *** map.size(): " + map.size());
 					value = (String)map.get(key);
 //					Log.customer.debug("%s *** Key/Value: %s/%s",ClassName,key,value);
 					br.close();
 				}
 				catch (IOException e) {
 					Log.customer.debug("CatCommonUtil *** IOException: %s", ClassName, e);
 				}
 			}
 		}
 		return value;
 	}

//	*** Returns hash value for key if found in file (CSVCommodityCodeMapReader version) ***
 	public static String readHashValueFromFile (String key, String filename)
 			throws IOException, ParseException {

 		String value = null;
 	 	if (!StringUtil.nullOrEmptyOrBlankString(filename) && key != null) {
 	 		File file = new File(filename);
 //	 		Log.customer.debug("%s *** file: %s", ClassName, file);
 			if (file != null) {

 				/* AUL : Changed the calling of this constructor */
 				//CSVCommodityCodeMapReader map = new CSVCommodityCodeMapReader(file);
 	  	      	CSVCommodityCodeMapReader map = new CSVCommodityCodeMapReader(file, false);
 //	  	      	Log.customer.debug("%s *** map: %s", ClassName, map);
 	  	      	value = map.get(key);
 //	  	      	Log.customer.debug("%s *** applimit: %s ", ClassName, value);
 			}
 		}
 		return value;
 	}

//	*** Returns list of values matching key if found in file (can have duplicates)***
 	public static List makeValueListFromFile (String key, String filename) {

 		ArrayList valuelist = new ArrayList();
 		if (!StringUtil.nullOrEmptyOrBlankString(filename) && key != null) {
 			File file = new File(filename);
 //			Log.customer.debug("%s *** file: %s", ClassName, file);
 			if (file != null) {
 				try {
 					BufferedReader br = new BufferedReader(new FileReader(file));
 //		 			Log.customer.debug("%s *** br: %s", ClassName, br);
 		 			String line = null;
  					while ((line = br.readLine())!= null) {
 //						Log.customer.debug("%s *** line: %s", ClassName, line);
 						List values = parseParamString(line);
 //			 			Log.customer.debug("%s *** values: %s", ClassName, values);
 						if (values.size() > 1 && ((String)values.get(0)).equals(key)) {
 //							Log.customer.debug("CatCommonUtil *** Found match, adding value to list");
 							valuelist.add(values.get(1));
 						}
 					}
 //					Log.customer.debug("CatCommonUtil *** valuelist.size(): " + valuelist.size());
 					br.close();
 				}
 				catch (IOException e) {
 					Log.customer.debug("%s *** IOException: %s", ClassName, e);
 				}
 			}
 		}
 		return valuelist;
 	}

 	// Added for R4 - Returns adjusted Total Cost (adds any not-to-exceed amounts to Requisition.TotalCost)
 	// Use only for ProcureLineItemCollection

 	public static Money getNotToExceedTotal (BaseVector lines) {

 	    Money total = new Money(new BigDecimal("0"), Currency.getBaseCurrency());
 	    if (lines != null && !lines.isEmpty()) {
 	        int size = lines.size();
 	        Money nte_price = null;
 	        String reason = null;
 	        for (int i=0;i<size;i++) {
 	            ProcureLineItem pli = (ProcureLineItem)lines.get(i);
 	            Money price = pli.getDescription().getPrice();
 	            if (price.getAmount().compareTo(new BigDecimal(0)) == 0) {
	 	            nte_price = (Money)pli.getDottedFieldValue("Description.NotToExceedPrice");
	 	            reason = (String)pli.getDottedFieldValue("Description.ReasonCode");
	 	            Log.customer.debug("%s *** reason, maxPrice: %s, %s",ClassName,reason,nte_price);
	 	            if (nte_price != null && reason.indexOf("xceed") > -1) {
	 	                nte_price = nte_price.multiply(pli.getQuantity());
	 	                total.addTo(nte_price);
	 	                Log.customer.debug("%s *** adding to Total, new Total: %s",ClassName,total);
	 	            }
 	            }
 	        }
 	    }
 	    Log.customer.debug("%s *** total returned: %s",ClassName,total);
 	    return total;
 	}

	public static boolean isExpenseManagerRequired(String CCToSearch,String GL)
 	{
		String filename="config/variants/SAP/data/ExpenseManagers.csv";
                Log.customer.debug("Expense Manager required function started");
		if(!StringUtil.nullOrEmptyOrBlankString(filename)&&CCToSearch != null && GL != null)
		{
                        Log.customer.debug("Expense Manager Required : Filename and GL is not null ");
			File file = new File(filename);
			if (file != null)
			{
			 	try
			 	{
			 		BufferedReader br = new BufferedReader(new FileReader(file));
			 		String line = null;
			  		while ((line = br.readLine())!= null)
			  		{
						Log.customer.debug("Expense Manager : Inside while loop");
			 			List values = parseParamString(line);
						Log.customer.debug("CCToSearch: "+values.get(0)+"GL :"+values.get(1));
						Log.customer.debug("CC and GL to Compare :"+CCToSearch +"and "+GL);
			 			if (values.size() > 1 && ((String)values.get(0)).equals(CCToSearch)  && ((String)values.get(1)).equals(GL))
			 			{
							Log.customer.debug("Expense Manager : Condition success : Manager required");
			 				return true;
 						}
					}
					br.close();
				}
				catch(IOException e)
				{
					Log.customer.debug("%s *** IOException: %s", ClassName, e);
				}

			}

		}
		return false;
	}

 	public static boolean isCategoryManagerRequired(String CCToSearch,String CompanyCode, Money CCAmtToCompare)
 	{
 		String filename = "config/variants/SAP/data/CategoryManagers.csv";
 		//ArrayList valuelist = new ArrayList();
 		if (!StringUtil.nullOrEmptyOrBlankString(filename) && CCToSearch != null && CompanyCode != null && CCAmtToCompare != null) {
 			File file = new File(filename);
 			if (file != null) {
 				try {
 					BufferedReader br = new BufferedReader(new FileReader(file));
 		 			String line = null;
  					while ((line = br.readLine())!= null) {
 						List values = parseParamString(line);
 						if (values.size() > 1 && ((String)values.get(0)).equals(CCToSearch)  && ((String)values.get(1)).equals(CompanyCode) && (String)values.get(2)!=null && (String)values.get(3)!=null) {
 							String limit1 = (String)values.get(2);
 							String currency1 = (String)values.get(3);
 							Money limitAmt =  new Money(new BigDecimal(limit1), Currency.getCurrencyFromUniqueName(currency1));
 							if (CCAmtToCompare.compareTo(limitAmt)>=0){
 								return true;
 							}
 							//valuelist.add(values.get(1));
 						}
 					}
 					br.close();
 				}
 				catch (IOException e) {
 					Log.customer.debug("%s *** IOException: %s", ClassName, e);
 				}
 			}
 		}
 		return false;
 	}

 	public static String getCategoryManager(String CCToSearch,String CompanyCode, Money CCAmtToCompare)
 	{
 		String filename = "config/variants/SAP/data/CategoryManagers.csv";
 		//ArrayList valuelist = new ArrayList();
 		if (!StringUtil.nullOrEmptyOrBlankString(filename) && CCToSearch != null && CompanyCode != null && CCAmtToCompare != null) {
 			File file = new File(filename);
 			if (file != null) {
 				try {
 					BufferedReader br = new BufferedReader(new FileReader(file));
 		 			String line = null;
  					while ((line = br.readLine())!= null) {
 						List values = parseParamString(line);
 						if (values.size() > 1 && ((String)values.get(0)).equals(CCToSearch)  && ((String)values.get(1)).equals(CompanyCode) && (String)values.get(2)!=null && (String)values.get(3)!=null && (String)values.get(4)!=null) {
 							String limit1 = (String)values.get(2);
 							String currency1 = (String)values.get(3);
 							Money limitAmt =  new Money(new BigDecimal(limit1), Currency.getCurrencyFromUniqueName(currency1));
 							if (CCAmtToCompare.compareTo(limitAmt)>=0){
 								return (String)values.get(4);
 							}
 							//valuelist.add(values.get(1));
 						}
 					}
 					br.close();
 				}
 				catch (IOException e) {
 					Log.customer.debug("%s *** IOException: %s", ClassName, e);
 				}
 			}
 		}
 		return null;
 	}

 	public static String getCategoryManagerisWatcher(String CCToSearch,String CompanyCode, Money CCAmtToCompare)
 	{
 		String filename = "config/variants/SAP/data/CategoryManagers.csv";
 		//ArrayList valuelist = new ArrayList();
 		if (!StringUtil.nullOrEmptyOrBlankString(filename) && CCToSearch != null && CompanyCode != null && CCAmtToCompare != null) {
 			File file = new File(filename);
 			if (file != null) {
 				try {
 					BufferedReader br = new BufferedReader(new FileReader(file));
 		 			String line = null;
  					while ((line = br.readLine())!= null) {
 						List values = parseParamString(line);
 						if (values.size() > 1 && ((String)values.get(0)).equals(CCToSearch)  && ((String)values.get(1)).equals(CompanyCode) && (String)values.get(2)!=null && (String)values.get(3)!=null && (String)values.get(4)!=null  && (String)values.get(5)!=null) {
 							String limit1 = (String)values.get(2);
 							String currency1 = (String)values.get(3);
 							Money limitAmt =  new Money(new BigDecimal(limit1), Currency.getCurrencyFromUniqueName(currency1));
 							if (CCAmtToCompare.compareTo(limitAmt)>=0){
 								return (String)values.get(5);
 							}
 							//valuelist.add(values.get(1));
 						}
 					}
 					br.close();
 				}
 				catch (IOException e) {
 					Log.customer.debug("%s *** IOException: %s", ClassName, e);
 				}
 			}
 		}
 		return null;
 	}
	//for Contract
public static Role getRoleforSplitterRuleforContract(Approvable r,String var ,ContractRequestLineItem li )

{

					String ALL = "ALL";
	                Log.customer.debug("inside Contract: " );
	                String sapsource = (String)r.getDottedFieldValue("CompanyCode.SAPSource");
					String company = (String)r.getDottedFieldValue("CompanyCode.UniqueName");
					String Country = (String)r.getDottedFieldValue("CompanyCode.RegisteredAddress.Country.UniqueName");
					String SearchString = null;
					String shipto = (String)li.getShipTo().getUniqueName();
					String plantcode = shipto.substring(0,4);
					//This is added for Hazmat functioanlity
					if(var.equals("HM")|| var=="HM")
					{
					String plantcodebasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + plantcode;
					String shiptobasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto;
					String combasedrole = var + "_" + sapsource +"_"+ Country + "_" + company;
					String countrybasedrole = var + "_" + sapsource +"_"+ Country;
					String sapsourcebasedrole  = var + "_" + sapsource;

                    Log.customer.debug("plantcodebasedrole: " + plantcodebasedrole);
					Log.customer.debug("shiptobasedrole: " + shiptobasedrole);
					Log.customer.debug("combasedrole: " + combasedrole);
					Log.customer.debug("countrybasedrole: " + countrybasedrole);
					Log.customer.debug("sapsourcebasedrole: " + sapsourcebasedrole);
					Role plantcodebasedrole1 = Role.getRole(plantcodebasedrole);
					Log.customer.debug("plantcodebasedrole1: " + plantcodebasedrole1);
					Role shiptobasedrole1 = Role.getRole(shiptobasedrole);
					Log.customer.debug("shiptobasedrole1: " + shiptobasedrole1);
					Role combasedrole1 = Role.getRole(combasedrole);
					Log.customer.debug("combasedrole1: " + combasedrole1);
					Role countrybasedrole1 = Role.getRole(countrybasedrole);
					Log.customer.debug("countrybasedrole1: " + countrybasedrole1);
					Role sapsourcebasedrole1 = Role.getRole(sapsourcebasedrole);
					Log.customer.debug("sapsourcebasedrole1: " + sapsourcebasedrole1);
					//plant level Hazmat role *** WI275 starts here
					if((plantcodebasedrole1!=null) && (plantcodebasedrole1.getActive()))
					{
					Log.customer.debug("%s***adding plantcodebasedrole:%s ", ClassName, plantcodebasedrole1);
					return plantcodebasedrole1;//adding plant code level role for Hazmat
					}
					//WI275 ends here
					else if((shiptobasedrole1!=null) && (shiptobasedrole1.getActive()))
					{
					return shiptobasedrole1;
					}
					else if ((combasedrole1!=null) && (combasedrole1.getActive()))
					{
					return combasedrole1;
					}
					else if ((countrybasedrole1!=null) && (countrybasedrole1.getActive()))
					{
					return countrybasedrole1;
					}
					else if ((sapsourcebasedrole1!=null) && (sapsourcebasedrole1.getActive()))
					{
					return sapsourcebasedrole1;
					}

					}

					//this added  for Taxmanger Rule for Invoice
					if(var.equals("TM")|| var=="TM")
					{

					String  taxCode =  (String)li.getDottedFieldValue("TaxCode.UniqueName");
					Log.customer.debug("%s ::  taxCode",taxCode);

					String tmtaxbasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto+"_"+taxCode;
					Role tmtaxbasedrole1 = Role.getRole(tmtaxbasedrole);
					Log.customer.debug("tmtaxbasedrole1: " + tmtaxbasedrole1);

					if((tmtaxbasedrole1!=null) && (tmtaxbasedrole1.getActive()))
					{
					return tmtaxbasedrole1;
					}

					String tmtaxbasedroleallShipTo = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + ALL+"_"+taxCode;
					Role tmtaxbasedroleallShipTo1 = Role.getRole(tmtaxbasedroleallShipTo);
					Log.customer.debug("tmtaxbasedroleallShipTo1: " + tmtaxbasedroleallShipTo1);
					if((tmtaxbasedroleallShipTo1!=null) && (tmtaxbasedroleallShipTo1.getActive()))
					{
					return tmtaxbasedroleallShipTo1;
					}


					String tmtaxbasedroleallCompCode = var + "_" + sapsource +"_"+ Country + "_" + ALL +"_"+taxCode;
					Role tmtaxbasedroleallCompCode1 = Role.getRole(tmtaxbasedroleallCompCode);
					Log.customer.debug("tmtaxbasedroleallCompCode1: " + tmtaxbasedroleallCompCode1);
					if((tmtaxbasedroleallCompCode1!=null) && (tmtaxbasedroleallCompCode1.getActive()))
					{
					return tmtaxbasedroleallCompCode1;
					}

					String tmtaxbasedroleallCountry = var + "_" + sapsource +"_"+ ALL+"_"+taxCode;
					Role tmtaxbasedroleallCountry1 = Role.getRole(tmtaxbasedroleallCountry);
					Log.customer.debug("tmtaxbasedroleallCountry1: " + tmtaxbasedroleallCountry1);
					if((tmtaxbasedroleallCountry1!=null) && (tmtaxbasedroleallCountry1.getActive()))
					{
					return tmtaxbasedroleallCountry1;
					}


					String tmtaxbasedroleallSource = var + "_" + ALL+"_"+taxCode;
					Role tmtaxbasedroleallSource1 = Role.getRole(tmtaxbasedroleallSource);
					Log.customer.debug("tmtaxbasedroleallSource1: " + tmtaxbasedroleallSource1);
					if((tmtaxbasedroleallSource1!=null) && (tmtaxbasedroleallSource1.getActive()))
					{
					return tmtaxbasedroleallSource1;
					}


					String tmshiptobasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto;
					Role tmshiptobasedrole1 = Role.getRole(tmshiptobasedrole);
					Log.customer.debug("tmshiptobasedrole1: " + tmshiptobasedrole1);
					if((tmshiptobasedrole1!=null) && (tmshiptobasedrole1.getActive()))
					{
					return tmshiptobasedrole1;
					}


                    //plant level Tax Manager role *** WI275 starts here
                    String tmplantcodebasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + plantcode;
                    Role tmplantcodebasedrole1 = Role.getRole(tmplantcodebasedrole);
					Log.customer.debug("tmplantcodebasedrole1: " + tmplantcodebasedrole1);
					if((tmplantcodebasedrole1!=null) && (tmplantcodebasedrole1.getActive()))
					{
					Log.customer.debug("%s***adding plantcodebasedrole:%s ", ClassName, tmplantcodebasedrole1);
					return tmplantcodebasedrole1;//adding plant code level role for Tax Manager
					}
                    //WI275 ends here


					String tmcombasedrole = var + "_" + sapsource +"_"+ Country + "_" + company;
					Role tmcombasedrole1 = Role.getRole(tmcombasedrole);
					Log.customer.debug("tmcombasedrole1: " + tmcombasedrole1);
					if((tmcombasedrole1!=null) && (tmcombasedrole1.getActive()))
					{
					return tmcombasedrole1;
					}


					String tmcountrybasedrole = var + "_" + sapsource +"_"+ Country;
					Role tmcountrybasedrole1 = Role.getRole(tmcountrybasedrole);
					Log.customer.debug("tmcountrybasedrole1: " + tmcountrybasedrole1);
					if((tmcountrybasedrole1!=null) && (tmcountrybasedrole1.getActive()))
					{
					return tmcountrybasedrole1;
					}


					String tmsapsourcebasedrole  = var + "_" + sapsource;
					Role tmsapsourcebasedrole1 = Role.getRole(tmsapsourcebasedrole);
					Log.customer.debug("tmsapsourcebasedrole1: " + tmsapsourcebasedrole1);
					if((tmsapsourcebasedrole1!=null) && (tmsapsourcebasedrole1.getActive()))
					{
					return tmsapsourcebasedrole1;
					}

					// Return null if no role is found
					return null;
					}

					return null;
				}


	//Added by nag for Hazmat
public static Role getRoleforSplitterRule(Approvable r,String var ,ReqLineItem li )

{
					String ALL = "ALL";
					String sapsource = (String)r.getDottedFieldValue("CompanyCode.SAPSource");
					String company = (String)r.getDottedFieldValue("CompanyCode.UniqueName");
					String Country = (String)r.getDottedFieldValue("CompanyCode.RegisteredAddress.Country.UniqueName");
					String SearchString = null;
					String shipto = (String)li.getShipTo().getUniqueName();
					String plantcode = shipto.substring(0,4);
					// Start :  Q4 2013 - RSD103 - FDD 4/TDD 1.1)
					if(var.equals("CSCL")|| var=="CSCL")
					{
					String CSCLplantcodebasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + plantcode;
					String CSCLshiptobasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto;
					String CSCLcombasedrole = var + "_" + sapsource +"_"+ Country + "_" + company;
					String CSCLcountrybasedrole = var + "_" + sapsource +"_"+ Country;
					String CSCLsapsourcebasedrole  = var + "_" + sapsource;

                    Log.customer.debug("CSCLplantcodebasedrole: " + CSCLplantcodebasedrole);
					Log.customer.debug("CSCLshiptobasedrole: " + CSCLshiptobasedrole);
					Log.customer.debug("CSCLcombasedrole: " + CSCLcombasedrole);
					Log.customer.debug("CSCLcountrybasedrole: " + CSCLcountrybasedrole);
					Log.customer.debug("CSCLsapsourcebasedrole: " + CSCLsapsourcebasedrole);
					
					Role CSCLplantcodebasedrole1 = Role.getRole(CSCLplantcodebasedrole);
					Log.customer.debug("CSCLplantcodebasedrole1: " + CSCLplantcodebasedrole1);
					
					Role CSCLshiptobasedrole1 = Role.getRole(CSCLshiptobasedrole);
					Log.customer.debug("shiptobasedrole1: " + CSCLshiptobasedrole1);
					
					Role CSCLcombasedrole1 = Role.getRole(CSCLcombasedrole);
					Log.customer.debug("CSCLcombasedrole1: " + CSCLcombasedrole1);
					Role CSCLcountrybasedrole1 = Role.getRole(CSCLcountrybasedrole);
					Log.customer.debug("countrybasedrole1: " + CSCLcountrybasedrole1);
					Role CSCLsapsourcebasedrole1 = Role.getRole(CSCLsapsourcebasedrole);
					Log.customer.debug("CSCLsapsourcebasedrole1: " + CSCLsapsourcebasedrole1);
					
					//plant level Hazmat role *** WI275 starts here
					if((CSCLplantcodebasedrole1!=null) && (CSCLplantcodebasedrole1.getActive()))
					{
					Log.customer.debug("%s***adding CSCLplantcodebasedrole:%s ", ClassName, CSCLplantcodebasedrole1);
					return CSCLplantcodebasedrole1;//adding plant code level role for Hazmat
					}
					//WI275 ends here
					else if((CSCLshiptobasedrole1!=null) && (CSCLshiptobasedrole1.getActive()))
					{
					return CSCLshiptobasedrole1;
					}
					else if ((CSCLcombasedrole1!=null) && (CSCLcombasedrole1.getActive()))
					{
					return CSCLcombasedrole1;
					}
					else if ((CSCLcountrybasedrole1!=null) && (CSCLcountrybasedrole1.getActive()))
					{
					return CSCLcountrybasedrole1;
					}
					else if ((CSCLsapsourcebasedrole1!=null) && (CSCLsapsourcebasedrole1.getActive()))
					{
					return CSCLsapsourcebasedrole1;
					}

					}
					// End Q4 2013 - RSD103 - FDD 4/TDD 1.1	
					
					//This is added for Hazmat functioanlity
					if(var.equals("HM")|| var=="HM")
					{
					String plantcodebasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + plantcode;
					String shiptobasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto;
					String combasedrole = var + "_" + sapsource +"_"+ Country + "_" + company;
					String countrybasedrole = var + "_" + sapsource +"_"+ Country;
					String sapsourcebasedrole  = var + "_" + sapsource;
					//Log.customer.debug("SearchString1: " + SearchString);
					Log.customer.debug("plantcodebasedrole: " + plantcodebasedrole);
					Log.customer.debug("shiptobasedrole: " + shiptobasedrole);
					Log.customer.debug("combasedrole: " + combasedrole);
					Log.customer.debug("countrybasedrole: " + countrybasedrole);
					Log.customer.debug("sapsourcebasedrole: " + sapsourcebasedrole);
					Role plantcodebasedrole1 = Role.getRole(plantcodebasedrole);
					Log.customer.debug("plantcodebasedrole1: " + plantcodebasedrole1);
					Role shiptobasedrole1 = Role.getRole(shiptobasedrole);
					Log.customer.debug("shiptobasedrole1: " + shiptobasedrole1);
					Role combasedrole1 = Role.getRole(combasedrole);
					Log.customer.debug("combasedrole1: " + combasedrole1);
					Role countrybasedrole1 = Role.getRole(countrybasedrole);
					Log.customer.debug("countrybasedrole1: " + countrybasedrole1);
					Role sapsourcebasedrole1 = Role.getRole(sapsourcebasedrole);
					Log.customer.debug("sapsourcebasedrole1: " + sapsourcebasedrole1);
					//plant level Hazmat role *** WI275 starts here
					if((plantcodebasedrole1!=null) && (plantcodebasedrole1.getActive()))
					{
					Log.customer.debug("%s***adding plantcodebasedrole:%s ", ClassName, plantcodebasedrole1);
					return plantcodebasedrole1;//adding plant code level role for Hazmat
					}
					//WI275 ends here
					else if((shiptobasedrole1!=null) && (shiptobasedrole1.getActive()))
					{
					return shiptobasedrole1;
					}
					else if ((combasedrole1!=null) && (combasedrole1.getActive()))
					{
					return combasedrole1;
					}
					else if ((countrybasedrole1!=null) && (countrybasedrole1.getActive()))
					{
					return countrybasedrole1;
					}
					else if ((sapsourcebasedrole1!=null) && (sapsourcebasedrole1.getActive()))
					{
					return sapsourcebasedrole1;
					}

					}
	//	This is added for Capital IT Approver functioanlity -  Starts

					if(var.equals("CI")|| var=="CI")
					{

					String cicategory = (String)li.getDottedFieldValue("Description.CommonCommodityCode.UniqueName");
					cicategory = cicategory.substring(0,2);

					String cicategoryrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto+"_"+cicategory;
					Role cipmcategoryrole = Role.getRole(cicategoryrole);
					Log.customer.debug("cipmcategoryrole: " +cipmcategoryrole );

					if((cipmcategoryrole!=null) && (cipmcategoryrole.getActive()))
					{
					return cipmcategoryrole;
					}



					String cicategoryroleallShipto = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + ALL +"_"+cicategory;
					Role cicategoryroleallShiptorole = Role.getRole(cicategoryroleallShipto);
					Log.customer.debug("cicategoryroleallShiptorole: " +cicategoryroleallShiptorole );

					if ((cicategoryroleallShiptorole!=null) && (cicategoryroleallShiptorole.getActive()))
					{
					return cicategoryroleallShiptorole;
					}



					String cicategoryroleallCompany = var + "_" + sapsource +"_"+ Country + "_" + ALL +"_"+cicategory;
					Role cicategoryroleallCompanyrole = Role.getRole(cicategoryroleallCompany);
					Log.customer.debug("cicategoryroleallCompanyrole: " +cicategoryroleallCompanyrole );
					if ((cicategoryroleallCompanyrole!=null) && (cicategoryroleallCompanyrole.getActive()))
					{
						return cicategoryroleallCompanyrole;
					}


					String cicategoryroleallCountry = var + "_" + sapsource +"_"+ ALL +"_"+cicategory;
					Role cicategoryroleallCountryrole = Role.getRole(cicategoryroleallCountry);
					Log.customer.debug("cicategoryroleallCountryrole: " +cicategoryroleallCountryrole );
					if ((cicategoryroleallCountryrole!=null) && (cicategoryroleallCountryrole.getActive()))
					{
					return cicategoryroleallCountryrole;
					}


					String cicategoryroleallSource = var + "_" + ALL +"_"+cicategory;
					Role cicategoryroleallSourcerole = Role.getRole(cicategoryroleallSource);
					Log.customer.debug("cicategoryroleallSourcerole: " +cicategoryroleallSourcerole );

					if ((cicategoryroleallSourcerole!=null) && (cicategoryroleallSourcerole.getActive()))
					{
					return cicategoryroleallSourcerole;
					}



					String cipmshiptobasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto;
					Role cipmshiptobasedrole1 = Role.getRole(cipmshiptobasedrole);
					Log.customer.debug("cipmshiptobasedrole1: " + cipmshiptobasedrole1);

					if ((cipmshiptobasedrole1!=null) && (cipmshiptobasedrole1.getActive()))
					{
					return cipmshiptobasedrole1;
					}

					//plant level Capital IT approver role *** WI275 starts here
                    String cipmplantcodebasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + plantcode;
					Role cipmplantcodebasedrole1 = Role.getRole(cipmplantcodebasedrole);
					Log.customer.debug("cipmplantcodebasedrole1: " + cipmplantcodebasedrole1);

					if ((cipmplantcodebasedrole1!=null) && (cipmplantcodebasedrole1.getActive()))
					{
					Log.customer.debug("%s***adding plantcodebasedrole:%s ", ClassName, cipmplantcodebasedrole1);
					return cipmplantcodebasedrole1;//adding plant code level role for Capital IT approver
					}
					//WI275 ends here


					String cipmcombasedrole = var + "_" + sapsource +"_"+ Country + "_" + company;
					Role cipmcombasedrole1 = Role.getRole(cipmcombasedrole);
					Log.customer.debug("cipmcombasedrole1: " + cipmcombasedrole1);
					if ((cipmcombasedrole1!=null) && (cipmcombasedrole1.getActive()))
					{
					return cipmcombasedrole1;
					}



					String cipmcountrybasedrole = var + "_" + sapsource +"_"+ Country;
					Role cipmcountrybasedrole1 = Role.getRole(cipmcountrybasedrole);
					Log.customer.debug("cipmcountrybasedrole1: " + cipmcountrybasedrole1);
					if ((cipmcountrybasedrole1!=null) && (cipmcountrybasedrole1.getActive()))
					{
					return cipmcountrybasedrole1;
					}


					String cipmsapsourcebasedrole  = var + "_" + sapsource;
					Role cipmsapsourcebasedrole1 = Role.getRole(cipmsapsourcebasedrole);
					Log.customer.debug("cipmsapsourcebasedrole1: " + cipmsapsourcebasedrole1);
					if ((cipmsapsourcebasedrole1!=null) && (cipmsapsourcebasedrole1.getActive()))
					{
					return cipmsapsourcebasedrole1;
					}

					//	If role did not found then return null
					return null;
				}


					//	This is added for Capital IT Approver functioanlity -  Ends

					//This is added for Block Admin functioanlity
					if(var.equals("BA")|| var=="BA")
					{

					String supplier = (String)li.getDottedFieldValue("SupplierLocation.BlockIndicator");

					String basupplierbasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto+"_"+supplier;
					Role basupplierbasedrole1 = Role.getRole(basupplierbasedrole);
					Log.customer.debug("basupplierbasedrole1: " +basupplierbasedrole1 );
					if((basupplierbasedrole1!=null) && (basupplierbasedrole1.getActive()))
					{
					return basupplierbasedrole1;
					}


					String basupplierbasedroleallShipTo = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + ALL+"_"+supplier;
					Role basupplierbasedroleallShipTo1 = Role.getRole(basupplierbasedroleallShipTo);
					Log.customer.debug("basupplierbasedroleallShipTo1: " +basupplierbasedroleallShipTo1 );
					if((basupplierbasedroleallShipTo1!=null) && (basupplierbasedroleallShipTo1.getActive()))
					{
					return basupplierbasedroleallShipTo1;
					}


					String basupplierbasedroleallCompCode = var + "_" + sapsource +"_"+ Country + "_" + ALL + "_"+supplier;
					Role basupplierbasedroleallCompCode1 = Role.getRole(basupplierbasedroleallCompCode);
					Log.customer.debug("basupplierbasedroleallCompCode1: " +basupplierbasedroleallCompCode1 );
					if((basupplierbasedroleallCompCode1!=null) && (basupplierbasedroleallCompCode1.getActive()))
					{
					return basupplierbasedroleallCompCode1;
					}

					String basupplierbasedroleallCountry = var + "_" + sapsource +"_"+ ALL +"_"+supplier;
					Role basupplierbasedroleallCountry1 = Role.getRole(basupplierbasedroleallCountry);
					Log.customer.debug("basupplierbasedroleallCountry1: " +basupplierbasedroleallCountry1 );
					if((basupplierbasedroleallCountry1!=null) && (basupplierbasedroleallCountry1.getActive()))
					{
					return basupplierbasedroleallCountry1;
					}


					String basupplierbasedroleallSource = var + "_" + sapsource +"_"+ ALL +"_"+supplier;
					Role basupplierbasedroleallSource1 = Role.getRole(basupplierbasedroleallSource);
					Log.customer.debug("basupplierbasedroleallSource1: " +basupplierbasedroleallSource1 );
					if((basupplierbasedroleallSource1!=null) && (basupplierbasedroleallSource1.getActive()))
					{
					return basupplierbasedroleallSource1;
					}


					String bashiptobasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto;
					Role bashiptobasedrole1 = Role.getRole(bashiptobasedrole);
					Log.customer.debug("bashiptobasedrole1: " + bashiptobasedrole1);
					if((bashiptobasedrole1!=null) && (bashiptobasedrole1.getActive()))
					{
					return bashiptobasedrole1;
					}

                    //plant level Block Admin role *** WI275 starts here
                    String baplantcodebasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + plantcode;
					Role baplantcodebasedrole1 = Role.getRole(baplantcodebasedrole);
					Log.customer.debug("baplantcodebasedrole1: " + baplantcodebasedrole1);
					if((baplantcodebasedrole1!=null) && (baplantcodebasedrole1.getActive()))
					{
					Log.customer.debug("%s***adding plantcodebasedrole:%s ", ClassName, baplantcodebasedrole1);
					return baplantcodebasedrole1;//adding plant code level role for Block Admin
					}
                    //WI275 ends here

					String bacombasedrole = var + "_" + sapsource +"_"+ Country + "_" + company;
					Role bacombasedrole1 = Role.getRole(bacombasedrole);
					Log.customer.debug("bacombasedrole1: " + bacombasedrole1);
					if((bacombasedrole1!=null) && (bacombasedrole1.getActive()))
					{
					return bacombasedrole1;
					}

					String bacountrybasedrole = var + "_" + sapsource +"_"+ Country;
					Role bacountrybasedrole1 = Role.getRole(bacountrybasedrole);
					Log.customer.debug("bacountrybasedrole1: " + bacountrybasedrole1);
					if((bacountrybasedrole1!=null) && (bacountrybasedrole1.getActive()))
					{
					return bacountrybasedrole1;
					}


					String basapsourcebasedrole  = var + "_" + sapsource;
					Role basapsourcebasedrole1 = Role.getRole(basapsourcebasedrole);
					Log.customer.debug("basapsourcebasedrole1: " + basapsourcebasedrole1);
					if((basapsourcebasedrole1!=null) && (basapsourcebasedrole1.getActive()))
					{
					return basapsourcebasedrole1;
					}

					// If there is no rele available then return null
					return null;

					}
					//this added  for Taxmanger Rule for Invoice
					if(var.equals("TM")|| var=="TM")
					{

					String  taxCode =  (String)li .getDottedFieldValue("TaxCode.UniqueName");
					Log.customer.debug("%s ::  taxCode",taxCode);

					String tmtaxbasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto+"_"+taxCode;
					Role tmtaxbasedrole1 = Role.getRole(tmtaxbasedrole);
					Log.customer.debug("tmtaxbasedrole1: " + tmtaxbasedrole1);

					if((tmtaxbasedrole1!=null) && (tmtaxbasedrole1.getActive()))
					{
					return tmtaxbasedrole1;
					}

					String tmtaxbasedroleallShipTo = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + ALL+"_"+taxCode;
					Role tmtaxbasedroleallShipTo1 = Role.getRole(tmtaxbasedroleallShipTo);
					Log.customer.debug("tmtaxbasedroleallShipTo1: " + tmtaxbasedroleallShipTo1);
					if((tmtaxbasedroleallShipTo1!=null) && (tmtaxbasedroleallShipTo1.getActive()))
					{
					return tmtaxbasedroleallShipTo1;
					}


					String tmtaxbasedroleallCompCode = var + "_" + sapsource +"_"+ Country + "_" + ALL +"_"+taxCode;
					Role tmtaxbasedroleallCompCode1 = Role.getRole(tmtaxbasedroleallCompCode);
					Log.customer.debug("tmtaxbasedroleallCompCode1: " + tmtaxbasedroleallCompCode1);
					if((tmtaxbasedroleallCompCode1!=null) && (tmtaxbasedroleallCompCode1.getActive()))
					{
					return tmtaxbasedroleallCompCode1;
					}

					String tmtaxbasedroleallCountry = var + "_" + sapsource +"_"+ ALL+"_"+taxCode;
					Role tmtaxbasedroleallCountry1 = Role.getRole(tmtaxbasedroleallCountry);
					Log.customer.debug("tmtaxbasedroleallCountry1: " + tmtaxbasedroleallCountry1);
					if((tmtaxbasedroleallCountry1!=null) && (tmtaxbasedroleallCountry1.getActive()))
					{
					return tmtaxbasedroleallCountry1;
					}


					String tmtaxbasedroleallSource = var + "_" + ALL+"_"+taxCode;
					Role tmtaxbasedroleallSource1 = Role.getRole(tmtaxbasedroleallSource);
					Log.customer.debug("tmtaxbasedroleallSource1: " + tmtaxbasedroleallSource1);
					if((tmtaxbasedroleallSource1!=null) && (tmtaxbasedroleallSource1.getActive()))
					{
					return tmtaxbasedroleallSource1;
					}


					String tmshiptobasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto;
					Role tmshiptobasedrole1 = Role.getRole(tmshiptobasedrole);
					Log.customer.debug("tmshiptobasedrole1: " + tmshiptobasedrole1);
					if((tmshiptobasedrole1!=null) && (tmshiptobasedrole1.getActive()))
					{
					return tmshiptobasedrole1;
					}

                    //plant level Tax Manager role *** WI275 starts here
                    String tmplantcodebasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + plantcode;
					Role tmplantcodebasedrole1 = Role.getRole(tmplantcodebasedrole);
					Log.customer.debug("tmplantcodebasedrole1: " + tmplantcodebasedrole1);
					if((tmplantcodebasedrole1!=null) && (tmplantcodebasedrole1.getActive()))
					{
					Log.customer.debug("%s***adding plantcodebasedrole:%s ", ClassName, tmplantcodebasedrole1);
					return tmplantcodebasedrole1;//adding plant code level role for Tax Manager
					}
                    //WI275 ends here

					String tmcombasedrole = var + "_" + sapsource +"_"+ Country + "_" + company;
					Role tmcombasedrole1 = Role.getRole(tmcombasedrole);
					Log.customer.debug("tmcombasedrole1: " + tmcombasedrole1);
					if((tmcombasedrole1!=null) && (tmcombasedrole1.getActive()))
					{
					return tmcombasedrole1;
					}


					String tmcountrybasedrole = var + "_" + sapsource +"_"+ Country;
					Role tmcountrybasedrole1 = Role.getRole(tmcountrybasedrole);
					Log.customer.debug("tmcountrybasedrole1: " + tmcountrybasedrole1);
					if((tmcountrybasedrole1!=null) && (tmcountrybasedrole1.getActive()))
					{
					return tmcountrybasedrole1;
					}


					String tmsapsourcebasedrole  = var + "_" + sapsource;
					Role tmsapsourcebasedrole1 = Role.getRole(tmsapsourcebasedrole);
					Log.customer.debug("tmsapsourcebasedrole1: " + tmsapsourcebasedrole1);
					if((tmsapsourcebasedrole1!=null) && (tmsapsourcebasedrole1.getActive()))
					{
					return tmsapsourcebasedrole1;
					}

					// Return null if no role is found
					return null;
					}

					//}


					if(var.equals("PM")|| var=="PM" || var.equals("IP") || var =="IP" )
					{
					// Code added for PCL
					//Added by Naresh for WI-285: Adding the null check condition for CustomShipTo field.
					 String customShipto = (String)r.getFieldValue("CustomShipTo");
					 Log.customer.debug("customShipto in IP: " + customShipto);
					 Log.customer.debug("plantcode before substring in IP: " + plantcode);
					 if(customShipto != null){
					 plantcode = customShipto.substring(0,4);
					 Log.customer.debug("plantcode after substring in IP: " + plantcode);
				 	}
				 	//Code ended by Naresh: WI-285
					// code ended for PCL
					String category = (String)li.getDottedFieldValue("Description.CommonCommodityCode.UniqueName");
					category = category.substring(0,2);

					String categoryrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto+"_"+category;
					Role pmcategoryrole = Role.getRole(categoryrole);
					Log.customer.debug("pmcategoryrole: " +pmcategoryrole );

					if((pmcategoryrole!=null) && (pmcategoryrole.getActive()))
					{
					return pmcategoryrole;
					}


					String categoryroleallShipTo = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto+"_"+category;
					Role categoryroleallShipTorole = Role.getRole(categoryroleallShipTo);
					Log.customer.debug("categoryroleallShipTorole: " +categoryroleallShipTorole );
					if((categoryroleallShipTorole!=null) && (categoryroleallShipTorole.getActive()))
					{
					return categoryroleallShipTorole;
					}


					String categoryroleallCompCode = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + ALL +"_"+category;
					Role categoryroleallCompCoderole = Role.getRole(categoryroleallCompCode);
					Log.customer.debug("categoryroleallCompCoderole: " +categoryroleallCompCoderole );
					if((categoryroleallCompCoderole!=null) && (categoryroleallCompCoderole.getActive()))
					{
					return categoryroleallCompCoderole;
					}


					String categoryroleallCountry = var + "_" + sapsource +"_"+ Country + "_" + ALL + "_"+category;
					Role categoryroleallCountryrole = Role.getRole(categoryroleallCountry);
					Log.customer.debug("categoryroleallCountryrole: " +categoryroleallCountryrole );
					if((categoryroleallCountryrole!=null) && (categoryroleallCountryrole.getActive()))
					{
					return categoryroleallCountryrole;
					}



					String categoryroleallSource = var + "_" + ALL +"_"+category;
					Role categoryroleallSourcerole = Role.getRole(categoryroleallSource);
					Log.customer.debug("categoryroleallSourcerole: " +categoryroleallSourcerole );
					if((categoryroleallSourcerole!=null) && (categoryroleallSourcerole.getActive()))
					{
					return categoryroleallSourcerole;
					}



					String pmshiptobasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto;
					Role pmshiptobasedrole1 = Role.getRole(pmshiptobasedrole);
					Log.customer.debug("pmshiptobasedrole1: " + pmshiptobasedrole1);
					if((pmshiptobasedrole1!=null) && (pmshiptobasedrole1.getActive()))
					{
					return pmshiptobasedrole1;
					}


                    //plant level PM and Indirect Purchasing role *** WI275 starts here
                    String pmplantcodebasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + plantcode;
					Role pmplantcodebasedrole1 = Role.getRole(pmplantcodebasedrole);
					Log.customer.debug("pmplantcodebasedrole1: " + pmplantcodebasedrole1);
					if((pmplantcodebasedrole1!=null) && (pmplantcodebasedrole1.getActive()))
					{
					Log.customer.debug("%s***adding plantcodebasedrole:%s ", ClassName, pmplantcodebasedrole1);
					return pmplantcodebasedrole1;//adding plant code level role for PM or IP
					}
					//WI275 ends here


					String pmcombasedrole = var + "_" + sapsource +"_"+ Country + "_" + company;
					Role pmcombasedrole1 = Role.getRole(pmcombasedrole);
					Log.customer.debug("pmcombasedrole1: " + pmcombasedrole1);
					if((pmcombasedrole1!=null) && (pmcombasedrole1.getActive()))
					{
					return pmcombasedrole1;
					}


					String pmcountrybasedrole = var + "_" + sapsource +"_"+ Country;
					Role pmcountrybasedrole1 = Role.getRole(pmcountrybasedrole);
					Log.customer.debug("pmcountrybasedrole1: " + pmcountrybasedrole1);
					if((pmcountrybasedrole1!=null) && (pmcountrybasedrole1.getActive()))
					{
					return pmcountrybasedrole1;
					}


					String pmsapsourcebasedrole  = var + "_" + sapsource;
					Role pmsapsourcebasedrole1 = Role.getRole(pmsapsourcebasedrole);
					Log.customer.debug("pmsapsourcebasedrole1: " + pmsapsourcebasedrole1);
					if((pmsapsourcebasedrole1!=null) && (pmsapsourcebasedrole1.getActive()))
					{
					return pmsapsourcebasedrole1;
					}

					// return null if no role is found
					return null;

			}
					return null;
	}
	//Added for InvoiceManager Role WorkItem 87 Starts here

		public static Role getRoleforSplitterRuleforInvoice(Approvable r,String var, InvoiceReconciliationLineItem irli)

		{
			Role returnRole = null;
			try {

				String sapSource = (String) r.getDottedFieldValue("CompanyCode.SAPSource");
				String company = (String) r.getDottedFieldValue("CompanyCode.UniqueName");
				String country = (String) r.getDottedFieldValue("CompanyCode.RegisteredAddress.Country.UniqueName");
				String SearchString = null;
				String shipToInv = (String) irli.getShipTo().getUniqueName();
				String plantCodeInv = shipToInv.substring(0, 4);
				
				// Start :  Q4 2013 - RSD103 - FDD 4/TDD 1.1)
				if(var.equals("CSCL")|| var=="CSCL")
				{
				String CSCLplantcodebasedrole = var + "_" + sapSource +"_"+ country + "_" + company +"_" + plantCodeInv;
				String CSCLshiptobasedrole = var + "_" + sapSource +"_"+ country + "_" + company +"_" + shipToInv;
				String CSCLcombasedrole = var + "_" + sapSource +"_"+ country + "_" + company;
				String CSCLcountrybasedrole = var + "_" + sapSource +"_"+ country;
				String CSCLsapsourcebasedrole  = var + "_" + sapSource;

                Log.customer.debug("CSCLplantcodebasedrole: " + CSCLplantcodebasedrole);
				Log.customer.debug("CSCLshiptobasedrole: " + CSCLshiptobasedrole);
				Log.customer.debug("CSCLcombasedrole: " + CSCLcombasedrole);
				Log.customer.debug("CSCLcountrybasedrole: " + CSCLcountrybasedrole);
				Log.customer.debug("CSCLsapsourcebasedrole: " + CSCLsapsourcebasedrole);
				
				Role CSCLplantcodebasedrole1 = Role.getRole(CSCLplantcodebasedrole);
				Log.customer.debug("CSCLplantcodebasedrole1: " + CSCLplantcodebasedrole1);
				
				Role CSCLshiptobasedrole1 = Role.getRole(CSCLshiptobasedrole);
				Log.customer.debug("shiptobasedrole1: " + CSCLshiptobasedrole1);
				
				Role CSCLcombasedrole1 = Role.getRole(CSCLcombasedrole);
				Log.customer.debug("CSCLcombasedrole1: " + CSCLcombasedrole1);
				Role CSCLcountrybasedrole1 = Role.getRole(CSCLcountrybasedrole);
				Log.customer.debug("countrybasedrole1: " + CSCLcountrybasedrole1);
				Role CSCLsapsourcebasedrole1 = Role.getRole(CSCLsapsourcebasedrole);
				Log.customer.debug("CSCLsapsourcebasedrole1: " + CSCLsapsourcebasedrole1);
				
				//plant level Hazmat role *** WI275 starts here
				if((CSCLplantcodebasedrole1!=null) && (CSCLplantcodebasedrole1.getActive()))
				{
				Log.customer.debug("%s***adding CSCLplantcodebasedrole:%s ", ClassName, CSCLplantcodebasedrole1);
				return CSCLplantcodebasedrole1;//adding plant code level role for Hazmat
				}
				//WI275 ends here
				else if((CSCLshiptobasedrole1!=null) && (CSCLshiptobasedrole1.getActive()))
				{
				return CSCLshiptobasedrole1;
				}
				else if ((CSCLcombasedrole1!=null) && (CSCLcombasedrole1.getActive()))
				{
				return CSCLcombasedrole1;
				}
				else if ((CSCLcountrybasedrole1!=null) && (CSCLcountrybasedrole1.getActive()))
				{
				return CSCLcountrybasedrole1;
				}
				else if ((CSCLsapsourcebasedrole1!=null) && (CSCLsapsourcebasedrole1.getActive()))
				{
				return CSCLsapsourcebasedrole1;
				}

				}
				// End Q4 2013 - RSD103 - FDD 4/TDD 1.1	
				// This is added for InvoiceManager functioanlity
				if (var.equals("IM") || var == "IM")
				{
					String plantCodeBasedRoleInv = var + "_" + sapSource + "_"+ country + "_" + company + "_" + plantCodeInv;
					String shipToBasedRoleInv = var + "_" + sapSource + "_"+ country + "_" + company + "_" + shipToInv;
					String comBasedRoleInv = var + "_" + sapSource + "_" + country+ "_" + company;
					String countryBasedRoleInv = var + "_" + sapSource + "_"+ country;
					String sapSourceBasedRoleInv = var + "_" + sapSource;
					// Log.customer.debug("SearchString1: " + SearchString);
					Log.customer.debug("plantcodebasedroleinv: "+ plantCodeBasedRoleInv);
					Log.customer.debug("shiptobasedroleinv: " + shipToBasedRoleInv);
					Log.customer.debug("combasedroleinv: " + comBasedRoleInv);
					Log.customer.debug("countrybasedroleinv: "+ countryBasedRoleInv);
					Log.customer.debug("sapsourcebasedroleinv: "+ sapSourceBasedRoleInv);
					Role plantCodeBasedRoleInv1 = Role.getRole(plantCodeBasedRoleInv);
					Log.customer.debug("plantcodebasedroleinv1: "+ plantCodeBasedRoleInv1);
					Role shipToBasedRoleInv1 = Role.getRole(shipToBasedRoleInv);
					Log.customer.debug("shiptobasedroleinv1: "+ shipToBasedRoleInv1);
					Role comBasedRoleInv1 = Role.getRole(comBasedRoleInv);
					Log.customer.debug("combasedroleinv1: " + comBasedRoleInv1);
					Role countryBasedRoleInv1 = Role.getRole(countryBasedRoleInv);
					Log.customer.debug("countrybasedroleinv1: "+ countryBasedRoleInv1);
					Role sapSourceBasedRoleInv1 = Role.getRole(sapSourceBasedRoleInv);
					Log.customer.debug("sapsourcebasedroleinv1: "+ sapSourceBasedRoleInv1);
					Role approverInv = Role.getRole("Invoice Manager");
					Log.customer.debug("Invoice Manager Role" + approverInv);

					if ((plantCodeBasedRoleInv1 != null)&& (plantCodeBasedRoleInv1.getActive()))
						{
						Log.customer.debug("%s***adding plantcodebasedroleinv:%s ",ClassName, plantCodeBasedRoleInv1);
						returnRole = plantCodeBasedRoleInv1;
						}
					else if ((shipToBasedRoleInv1 != null)&& (shipToBasedRoleInv1.getActive()))
						{
						returnRole = shipToBasedRoleInv1;
						}
					else if ((comBasedRoleInv1 != null)&& (comBasedRoleInv1.getActive()))
						{
						returnRole = comBasedRoleInv1;
						}
					else if ((countryBasedRoleInv1 != null)&& (countryBasedRoleInv1.getActive()))
						{
						returnRole = countryBasedRoleInv1;
						}
					else if ((sapSourceBasedRoleInv1 != null)&& (sapSourceBasedRoleInv1.getActive()))
						{
						returnRole = sapSourceBasedRoleInv1;
						}
					else if ((approverInv != null) && (approverInv.getActive()))
					{
						returnRole = approverInv;
					}
				}
			}
			catch (Exception exp)
			{
				Log.customer.debug("getRoleforSplitterRuleforInvoice: Exception occured "+ exp);
			}
			return returnRole;
	}

//WorkItem 87 Ends here

	//Added for adding Mach1 Tax Manager Approver for VERTEX

	public static Role getRoleforSplitterRuleForVertex(Approvable r,String var )

{
					String ALL = "ALL";
					String sapsource = (String)r.getDottedFieldValue("CompanyCode.SAPSource");
					String company = (String)r.getDottedFieldValue("CompanyCode.UniqueName");
					String Country = (String)r.getDottedFieldValue("CompanyCode.RegisteredAddress.Country.UniqueName");
					String SearchString = null;
					//String shipto = (String)li.getShipTo().getUniqueName();
					Log.customer.debug("sapsource: " + sapsource);
					Log.customer.debug("company: " + company);
					Log.customer.debug("Country: " + Country);
					//This is added for Hazmat functioanlity
					if(var.equals("HM")|| var=="HM")
					{
					//String shiptobasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto;
					String combasedrole = var + "_" + sapsource +"_"+ Country + "_" + company;
					String countrybasedrole = var + "_" + sapsource +"_"+ Country;
					String sapsourcebasedrole  = var + "_" + sapsource;
					//Log.customer.debug("SearchString1: " + SearchString);
					//Log.customer.debug("shiptobasedrole: " + shiptobasedrole);
					Log.customer.debug("combasedrole: " + combasedrole);
					Log.customer.debug("countrybasedrole: " + countrybasedrole);
					Log.customer.debug("sapsourcebasedrole: " + sapsourcebasedrole);
					/*Role shiptobasedrole1 = Role.getRole(shiptobasedrole);
					Log.customer.debug("shiptobasedrole1: " + shiptobasedrole1);*/
					Role combasedrole1 = Role.getRole(combasedrole);
					Log.customer.debug("combasedrole1: " + combasedrole1);
					Role countrybasedrole1 = Role.getRole(countrybasedrole);
					Log.customer.debug("countrybasedrole1: " + countrybasedrole1);
					Role sapsourcebasedrole1 = Role.getRole(sapsourcebasedrole);
					Log.customer.debug("sapsourcebasedrole1: " + sapsourcebasedrole1);
					/*if((shiptobasedrole1!=null) && (shiptobasedrole1.getActive()))
					{
					return shiptobasedrole1;
					}*/
					 if ((combasedrole1!=null) && (combasedrole1.getActive()))
					{
					return combasedrole1;
					}
					else if ((countrybasedrole1!=null) && (countrybasedrole1.getActive()))
					{
					return countrybasedrole1;
					}
					else if ((sapsourcebasedrole1!=null) && (sapsourcebasedrole1.getActive()))
					{
					return sapsourcebasedrole1;
					}

					}

					//	This is added for Capital IT Approver functioanlity -  Starts

					if(var.equals("CI")|| var=="CI")
					{

					/*SString cicategory = (String)li.getDottedFieldValue("Description.CommonCommodityCode.UniqueName");
					cicategory = cicategory.substring(0,2);

					tring cicategoryrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto+"_"+cicategory;
					Role cipmcategoryrole = Role.getRole(cicategoryrole);
					Log.customer.debug("cipmcategoryrole: " +cipmcategoryrole );

					if((cipmcategoryrole!=null) && (cipmcategoryrole.getActive()))
					{
					return cipmcategoryrole;
					}*/



					/*String cicategoryroleallShipto = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + ALL +"_"+cicategory;
					Role cicategoryroleallShiptorole = Role.getRole(cicategoryroleallShipto);
					Log.customer.debug("cicategoryroleallShiptorole: " +cicategoryroleallShiptorole );

					if ((cicategoryroleallShiptorole!=null) && (cicategoryroleallShiptorole.getActive()))
					{
					return cicategoryroleallShiptorole;
					}



					String cicategoryroleallCompany = var + "_" + sapsource +"_"+ Country + "_" + ALL +"_"+cicategory;
					Role cicategoryroleallCompanyrole = Role.getRole(cicategoryroleallCompany);
					Log.customer.debug("cicategoryroleallCompanyrole: " +cicategoryroleallCompanyrole );
					if ((cicategoryroleallCompanyrole!=null) && (cicategoryroleallCompanyrole.getActive()))
					{
						return cicategoryroleallCompanyrole;
					}


					String cicategoryroleallCountry = var + "_" + sapsource +"_"+ ALL +"_"+cicategory;
					Role cicategoryroleallCountryrole = Role.getRole(cicategoryroleallCountry);
					Log.customer.debug("cicategoryroleallCountryrole: " +cicategoryroleallCountryrole );
					if ((cicategoryroleallCountryrole!=null) && (cicategoryroleallCountryrole.getActive()))
					{
					return cicategoryroleallCountryrole;
					}


					String cicategoryroleallSource = var + "_" + ALL +"_"+cicategory;
					Role cicategoryroleallSourcerole = Role.getRole(cicategoryroleallSource);
					Log.customer.debug("cicategoryroleallSourcerole: " +cicategoryroleallSourcerole );

					if ((cicategoryroleallSourcerole!=null) && (cicategoryroleallSourcerole.getActive()))
					{
					return cicategoryroleallSourcerole;
					}
					*/


					/*String cipmshiptobasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto;
					Role cipmshiptobasedrole1 = Role.getRole(cipmshiptobasedrole);
					Log.customer.debug("cipmshiptobasedrole1: " + cipmshiptobasedrole1);

					if ((cipmshiptobasedrole1!=null) && (cipmshiptobasedrole1.getActive()))
					{
					return cipmshiptobasedrole1;
					}*/


					String cipmcombasedrole = var + "_" + sapsource +"_"+ Country + "_" + company;
					Role cipmcombasedrole1 = Role.getRole(cipmcombasedrole);
					Log.customer.debug("cipmcombasedrole1: " + cipmcombasedrole1);
					if ((cipmcombasedrole1!=null) && (cipmcombasedrole1.getActive()))
					{
					return cipmcombasedrole1;
					}


					String cipmcountrybasedrole = var + "_" + sapsource +"_"+ Country;
					Role cipmcountrybasedrole1 = Role.getRole(cipmcountrybasedrole);
					Log.customer.debug("cipmcountrybasedrole1: " + cipmcountrybasedrole1);
					if ((cipmcountrybasedrole1!=null) && (cipmcountrybasedrole1.getActive()))
					{
					return cipmcountrybasedrole1;
					}


					String cipmsapsourcebasedrole  = var + "_" + sapsource;
					Role cipmsapsourcebasedrole1 = Role.getRole(cipmsapsourcebasedrole);
					Log.customer.debug("cipmsapsourcebasedrole1: " + cipmsapsourcebasedrole1);
					if ((cipmsapsourcebasedrole1!=null) && (cipmsapsourcebasedrole1.getActive()))
					{
					return cipmsapsourcebasedrole1;
					}

					//	If role did not found then return null
					return null;
				}
					//	This is added for Capital IT Approver functioanlity -  Ends

					//This is added for Block Admin functioanlity
					if(var.equals("BA")|| var=="BA")
					{

					/*String supplier = (String)li.getDottedFieldValue("SupplierLocation.BlockIndicator");

					String basupplierbasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto+"_"+supplier;
					Role basupplierbasedrole1 = Role.getRole(basupplierbasedrole);
					Log.customer.debug("basupplierbasedrole1: " +basupplierbasedrole1 );
					if((basupplierbasedrole1!=null) && (basupplierbasedrole1.getActive()))
					{
					return basupplierbasedrole1;
					}

					String basupplierbasedroleallShipTo = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + ALL+"_"+supplier;
					Role basupplierbasedroleallShipTo1 = Role.getRole(basupplierbasedroleallShipTo);
					Log.customer.debug("basupplierbasedroleallShipTo1: " +basupplierbasedroleallShipTo1 );
					if((basupplierbasedroleallShipTo1!=null) && (basupplierbasedroleallShipTo1.getActive()))
					{
					return basupplierbasedroleallShipTo1;
					}


					String basupplierbasedroleallCompCode = var + "_" + sapsource +"_"+ Country + "_" + ALL + "_"+supplier;
					Role basupplierbasedroleallCompCode1 = Role.getRole(basupplierbasedroleallCompCode);
					Log.customer.debug("basupplierbasedroleallCompCode1: " +basupplierbasedroleallCompCode1 );
					if((basupplierbasedroleallCompCode1!=null) && (basupplierbasedroleallCompCode1.getActive()))
					{
					return basupplierbasedroleallCompCode1;
					}

					String basupplierbasedroleallCountry = var + "_" + sapsource +"_"+ ALL +"_"+supplier;
					Role basupplierbasedroleallCountry1 = Role.getRole(basupplierbasedroleallCountry);
					Log.customer.debug("basupplierbasedroleallCountry1: " +basupplierbasedroleallCountry1 );
					if((basupplierbasedroleallCountry1!=null) && (basupplierbasedroleallCountry1.getActive()))
					{
					return basupplierbasedroleallCountry1;
					}


					String basupplierbasedroleallSource = var + "_" + sapsource +"_"+ ALL +"_"+supplier;
					Role basupplierbasedroleallSource1 = Role.getRole(basupplierbasedroleallSource);
					Log.customer.debug("basupplierbasedroleallSource1: " +basupplierbasedroleallSource1 );
					if((basupplierbasedroleallSource1!=null) && (basupplierbasedroleallSource1.getActive()))
					{
					return basupplierbasedroleallSource1;
					}*/


					/*String bashiptobasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto;
					Role bashiptobasedrole1 = Role.getRole(bashiptobasedrole);
					Log.customer.debug("bashiptobasedrole1: " + bashiptobasedrole1);
					if((bashiptobasedrole1!=null) && (bashiptobasedrole1.getActive()))
					{
					return bashiptobasedrole1;
					}*/


					String bacombasedrole = var + "_" + sapsource +"_"+ Country + "_" + company;
					Role bacombasedrole1 = Role.getRole(bacombasedrole);
					Log.customer.debug("bacombasedrole1: " + bacombasedrole1);
					if((bacombasedrole1!=null) && (bacombasedrole1.getActive()))
					{
					return bacombasedrole1;
					}


					String bacountrybasedrole = var + "_" + sapsource +"_"+ Country;
					Role bacountrybasedrole1 = Role.getRole(bacountrybasedrole);
					Log.customer.debug("bacountrybasedrole1: " + bacountrybasedrole1);
					if((bacountrybasedrole1!=null) && (bacountrybasedrole1.getActive()))
					{
					return bacountrybasedrole1;
					}


					String basapsourcebasedrole  = var + "_" + sapsource;
					Role basapsourcebasedrole1 = Role.getRole(basapsourcebasedrole);
					Log.customer.debug("basapsourcebasedrole1: " + basapsourcebasedrole1);
					if((basapsourcebasedrole1!=null) && (basapsourcebasedrole1.getActive()))
					{
					return basapsourcebasedrole1;
					}

					// If there is no rele available then return null
					return null;

					}
					//this added  for Taxmanger Rule for Invoice
					if(var.equals("TM")|| var=="TM")
					{

					/*String  taxCode =  (String)li .getDottedFieldValue("TaxCode.UniqueName");
					Log.customer.debug("%s ::  taxCode",taxCode);

					String tmtaxbasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto+"_"+taxCode;
					Role tmtaxbasedrole1 = Role.getRole(tmtaxbasedrole);
					Log.customer.debug("tmtaxbasedrole1: " + tmtaxbasedrole1);

					if((tmtaxbasedrole1!=null) && (tmtaxbasedrole1.getActive()))
					{
					return tmtaxbasedrole1;
					}*/

					/*String tmtaxbasedroleallShipTo = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + ALL+"_"+taxCode;
					Role tmtaxbasedroleallShipTo1 = Role.getRole(tmtaxbasedroleallShipTo);
					Log.customer.debug("tmtaxbasedroleallShipTo1: " + tmtaxbasedroleallShipTo1);
					if((tmtaxbasedroleallShipTo1!=null) && (tmtaxbasedroleallShipTo1.getActive()))
					{
					return tmtaxbasedroleallShipTo1;
					}


					String tmtaxbasedroleallCompCode = var + "_" + sapsource +"_"+ Country + "_" + ALL +"_"+taxCode;
					Role tmtaxbasedroleallCompCode1 = Role.getRole(tmtaxbasedroleallCompCode);
					Log.customer.debug("tmtaxbasedroleallCompCode1: " + tmtaxbasedroleallCompCode1);
					if((tmtaxbasedroleallCompCode1!=null) && (tmtaxbasedroleallCompCode1.getActive()))
					{
					return tmtaxbasedroleallCompCode1;
					}

					String tmtaxbasedroleallCountry = var + "_" + sapsource +"_"+ ALL+"_"+taxCode;
					Role tmtaxbasedroleallCountry1 = Role.getRole(tmtaxbasedroleallCountry);
					Log.customer.debug("tmtaxbasedroleallCountry1: " + tmtaxbasedroleallCountry1);
					if((tmtaxbasedroleallCountry1!=null) && (tmtaxbasedroleallCountry1.getActive()))
					{
					return tmtaxbasedroleallCountry1;
					}


					String tmtaxbasedroleallSource = var + "_" + ALL+"_"+taxCode;
					Role tmtaxbasedroleallSource1 = Role.getRole(tmtaxbasedroleallSource);
					Log.customer.debug("tmtaxbasedroleallSource1: " + tmtaxbasedroleallSource1);
					if((tmtaxbasedroleallSource1!=null) && (tmtaxbasedroleallSource1.getActive()))
					{
					return tmtaxbasedroleallSource1;
					}
					*/

					/*String tmshiptobasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto;
					Role tmshiptobasedrole1 = Role.getRole(tmshiptobasedrole);
					Log.customer.debug("tmshiptobasedrole1: " + tmshiptobasedrole1);
					if((tmshiptobasedrole1!=null) && (tmshiptobasedrole1.getActive()))
					{
					return tmshiptobasedrole1;
					}*/



					String tmcombasedrole = var + "_" + sapsource +"_"+ Country + "_" + company;
					Role tmcombasedrole1 = Role.getRole(tmcombasedrole);
					Log.customer.debug("tmcombasedrole1: " + tmcombasedrole1);
					if((tmcombasedrole1!=null) && (tmcombasedrole1.getActive()))
					{
					return tmcombasedrole1;
					}


					String tmcountrybasedrole = var + "_" + sapsource +"_"+ Country;
					Role tmcountrybasedrole1 = Role.getRole(tmcountrybasedrole);
					Log.customer.debug("tmcountrybasedrole1: " + tmcountrybasedrole1);
					if((tmcountrybasedrole1!=null) && (tmcountrybasedrole1.getActive()))
					{
					return tmcountrybasedrole1;
					}


					String tmsapsourcebasedrole  = var + "_" + sapsource;
					Role tmsapsourcebasedrole1 = Role.getRole(tmsapsourcebasedrole);
					Log.customer.debug("tmsapsourcebasedrole1: " + tmsapsourcebasedrole1);
					if((tmsapsourcebasedrole1!=null) && (tmsapsourcebasedrole1.getActive()))
					{
					return tmsapsourcebasedrole1;
					}

					// Return null if no role is found
					return null;
					}

					//}


					if(var.equals("PM")|| var=="PM" || var.equals("IP") || var =="IP" )
					{
					/* shipto = (String)r.getFieldValue("CustomShipTo");
 					 Log.customer.debug("shipto in IP: " + shipto);
					String category = (String)li.getDottedFieldValue("Description.CommonCommodityCode.UniqueName");
					category = category.substring(0,2);

					String categoryrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto+"_"+category;
					Role pmcategoryrole = Role.getRole(categoryrole);
					Log.customer.debug("pmcategoryrole: " +pmcategoryrole );

					if((pmcategoryrole!=null) && (pmcategoryrole.getActive()))
					{
					return pmcategoryrole;
					}


					String categoryroleallShipTo = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto+"_"+category;
					Role categoryroleallShipTorole = Role.getRole(categoryroleallShipTo);
					Log.customer.debug("categoryroleallShipTorole: " +categoryroleallShipTorole );
					if((categoryroleallShipTorole!=null) && (categoryroleallShipTorole.getActive()))
					{
					return categoryroleallShipTorole;
					}


					String categoryroleallCompCode = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + ALL +"_"+category;
					Role categoryroleallCompCoderole = Role.getRole(categoryroleallCompCode);
					Log.customer.debug("categoryroleallCompCoderole: " +categoryroleallCompCoderole );
					if((categoryroleallCompCoderole!=null) && (categoryroleallCompCoderole.getActive()))
					{
					return categoryroleallCompCoderole;
					}


					String categoryroleallCountry = var + "_" + sapsource +"_"+ Country + "_" + ALL + "_"+category;
					Role categoryroleallCountryrole = Role.getRole(categoryroleallCountry);
					Log.customer.debug("categoryroleallCountryrole: " +categoryroleallCountryrole );
					if((categoryroleallCountryrole!=null) && (categoryroleallCountryrole.getActive()))
					{
					return categoryroleallCountryrole;
					}



					String categoryroleallSource = var + "_" + ALL +"_"+category;
					Role categoryroleallSourcerole = Role.getRole(categoryroleallSource);
					Log.customer.debug("categoryroleallSourcerole: " +categoryroleallSourcerole );
					if((categoryroleallSourcerole!=null) && (categoryroleallSourcerole.getActive()))
					{
					return categoryroleallSourcerole;
					}*/



					/*String pmshiptobasedrole = var + "_" + sapsource +"_"+ Country + "_" + company +"_" + shipto;
					Role pmshiptobasedrole1 = Role.getRole(pmshiptobasedrole);
					Log.customer.debug("pmshiptobasedrole1: " + pmshiptobasedrole1);
					if((pmshiptobasedrole1!=null) && (pmshiptobasedrole1.getActive()))
					{
					return pmshiptobasedrole1;
					}*/


					String pmcombasedrole = var + "_" + sapsource +"_"+ Country + "_" + company;
					Role pmcombasedrole1 = Role.getRole(pmcombasedrole);
					Log.customer.debug("pmcombasedrole1: " + pmcombasedrole1);
					if((pmcombasedrole1!=null) && (pmcombasedrole1.getActive()))
					{
					return pmcombasedrole1;
					}


					String pmcountrybasedrole = var + "_" + sapsource +"_"+ Country;
					Role pmcountrybasedrole1 = Role.getRole(pmcountrybasedrole);
					Log.customer.debug("pmcountrybasedrole1: " + pmcountrybasedrole1);
					if((pmcountrybasedrole1!=null) && (pmcountrybasedrole1.getActive()))
					{
					return pmcountrybasedrole1;
					}


					String pmsapsourcebasedrole  = var + "_" + sapsource;
					Role pmsapsourcebasedrole1 = Role.getRole(pmsapsourcebasedrole);
					Log.customer.debug("pmsapsourcebasedrole1: " + pmsapsourcebasedrole1);
					if((pmsapsourcebasedrole1!=null) && (pmsapsourcebasedrole1.getActive()))
					{
					return pmsapsourcebasedrole1;
					}

					// return null if no role is found
					return null;

			}
					return null;
	}

	//End of Vertex Code
	//Added by nag for generic method

	public static Role getCentralReceiverRole(Approvable r,String var)
	{

		String ALL = "ALL";
		if(var.equals("DU")|| var=="DU")
		{
			 ClusterRoot Company =(ClusterRoot)r.getFieldValue("CompanyCode");
			 String sapsource =null;
			 String companycode=null;
			 String Country = null;
			if(Company!=null)
			{
				 sapsource = (String)r.getDottedFieldValue("CompanyCode.SAPSource");
				 companycode = (String)r.getDottedFieldValue("CompanyCode.UniqueName");
				 Country = (String)r.getDottedFieldValue("CompanyCode.RegisteredAddress.Country.UniqueName");
				Log.customer.debug("inside Company Part: " );
				Log.customer.debug("companycode: " + companycode);
				Log.customer.debug("sapsource: " + sapsource);
				Log.customer.debug("Country: " + Country);


			}
			else
			{
				 Log.customer.debug("inside Else block: ");
				ariba.user.core.User requester = (ariba.user.core.User)r.getRequester();
				Log.customer.debug("requester: " + requester);
				ariba.common.core.User partuser = ariba.common.core.User.getPartitionedUser(requester,Base.getSession().getPartition());
				 Log.customer.debug("partuser: " + partuser);
				 companycode = (String)partuser.getDottedFieldValue("CompanyCode.UniqueName");
				 sapsource = (String)partuser.getDottedFieldValue("CompanyCode.SAPSource");
				 Country = (String)partuser.getDottedFieldValue("CompanyCode.RegisteredAddress.Country.UniqueName");
				Log.customer.debug("companycode: " + companycode);
				Log.customer.debug("sapsource: " + sapsource);
				Log.customer.debug("Country: " + Country);

			}
				String ducombasedrole = var + "_" + sapsource +"_"+ Country + "_" + companycode;
				String ducountrybasedrole = var + "_" + sapsource +"_"+ Country;
				String dusapsourcebasedrole  = var + "_" + sapsource;
				Role ducombasedrole1 = Role.getRole(ducombasedrole);
				Log.customer.debug("ducombasedrole1: " + ducombasedrole1);
				Role ducountrybasedrole1 = Role.getRole(ducountrybasedrole);
				Log.customer.debug("ducountrybasedrole1: " + ducountrybasedrole1);
				Role dusapsourcebasedrole1 = Role.getRole(dusapsourcebasedrole);
				Log.customer.debug("dusapsourcebasedrole1: " + dusapsourcebasedrole1);
				if ((ducombasedrole1!=null) && (ducombasedrole1.getActive()))
				{
				return ducombasedrole1;
				}
				else if ((ducountrybasedrole1!=null) && (ducountrybasedrole1.getActive()))
				{
				return ducountrybasedrole1;
				}
				else if ((dusapsourcebasedrole1!=null) && (dusapsourcebasedrole1.getActive()))
				{
				return dusapsourcebasedrole1;
				}
				else
				{
				}
		}
		if (r instanceof Receipt)
		{
			String category = null;
			String shipTo = null;
			String plantcode = null;
			String company = null;
			String country = null;
			String sapSource = null;


			if (r.getDottedFieldValue("Order")!=null)
			{
		    category = (String)r.getDottedFieldValue("Order.LineItems[0].Description.CommonCommodityCode.UniqueName");
			category = category.substring(0,2);
			shipTo = (String)r.getDottedFieldValue("Order.LineItems[0].ShipTo.UniqueName");
			plantcode = shipTo.substring(0,4);
			company = (String)r.getDottedFieldValue("Order.CompanyCode.UniqueName");
			country = (String)r.getDottedFieldValue("Order.CompanyCode.RegisteredAddress.Country.UniqueName");
			sapSource = (String)r.getDottedFieldValue("Order.CompanyCode.SAPSource");
			}
			else if(r.getDottedFieldValue("MasterAgreement")!=null)
			{
			    category = (String)r.getDottedFieldValue("MasterAgreement.LineItems[0].Description.CommonCommodityCode.UniqueName");
				category = category.substring(0,2);
				shipTo = (String)r.getDottedFieldValue("MasterAgreement.LineItems[0].ShipTo.UniqueName");
				plantcode = shipTo.substring(0,4);
				company = (String)r.getDottedFieldValue("MasterAgreement.CompanyCode.UniqueName");
				country = (String)r.getDottedFieldValue("MasterAgreement.CompanyCode.RegisteredAddress.Country.UniqueName");
				sapSource = (String)r.getDottedFieldValue("MasterAgreement.CompanyCode.SAPSource");
			} else
			{
				return null;
			}


			if(var.equals("CR")|| var=="CR")
          	{
      			String crcategoryrole = var + "_" + sapSource +"_"+ country + "_" + company +"_" + shipTo+"_"+category;
      			Role crcategoryrole1 = Role.getRole(crcategoryrole);
		  		Log.customer.debug("crcategoryrole: " +crcategoryrole );
		  		if((crcategoryrole1 != null) && (crcategoryrole1.getActive()))
				{
	 				return crcategoryrole1;
				}


      			String crcategoryallShipTo = var + "_" + sapSource +"_"+ country + "_" + company +"_" + ALL +"_"+category;
      			Role crcategoryallShipTorole = Role.getRole(crcategoryallShipTo);
		  		Log.customer.debug("crcategoryallShipTorole" +crcategoryallShipTorole );
		  		if((crcategoryallShipTorole != null) && (crcategoryallShipTorole.getActive()))
				{
	 				return crcategoryallShipTorole;
				}


      			String crcategoryallCompCode = var + "_" + sapSource +"_"+ country + "_" + ALL +"_" +category;
      			Role crcategoryallCompCoderole = Role.getRole(crcategoryallCompCode);
		  		Log.customer.debug("crcategoryallCompCoderole" +crcategoryallCompCoderole );
		  		if((crcategoryallCompCoderole != null) && (crcategoryallCompCoderole.getActive()))
				{
	 				return crcategoryallCompCoderole;
				}


      			String crcategoryallCountry = var + "_" + sapSource +"_"+ ALL + "_" + category;
      			Role crcategoryallCountryrole = Role.getRole(crcategoryallCountry);
		  		Log.customer.debug("crcategoryallCountryrole" +crcategoryallCountryrole );
		  		if((crcategoryallCountryrole != null) && (crcategoryallCountryrole.getActive()))
				{
	 				return crcategoryallCountryrole;
				}


      			String crcategoryallSource = var + "_" + ALL + "_" +category;
      			Role crcategoryallSourceyrole = Role.getRole(crcategoryallSource);
		  		Log.customer.debug("crcategoryallSourceyrole" +crcategoryallSourceyrole );
		  		if((crcategoryallSourceyrole != null) && (crcategoryallSourceyrole.getActive()))
				{
	 				return crcategoryallSourceyrole;
				}


				String crshiptobasedrole = var + "_" + sapSource +"_"+ country + "_" + company +"_" + shipTo;
				Role crshiptobasedrole1 = Role.getRole(crshiptobasedrole);
		  		Log.customer.debug("crshiptobasedrole1: " + crshiptobasedrole1);
		  		if((crshiptobasedrole1 != null) && (crshiptobasedrole1.getActive()))
				{
	 				return crshiptobasedrole1;
				}

				//plant level Central Receiving role *** WI275 starts here
				String crplantcodebasedrole = var + "_" + sapSource +"_"+ country + "_" + company +"_" + plantcode;
				Role crplantcodebasedrole1 = Role.getRole(crplantcodebasedrole);
				Log.customer.debug("crplantcodebasedrole1: " + crplantcodebasedrole1);
				if((crplantcodebasedrole1 != null) && (crplantcodebasedrole1.getActive()))
				{
					Log.customer.debug("%s***adding plantcodebasedrole:%s ", ClassName, crplantcodebasedrole1);
					return crplantcodebasedrole1;//adding plant code level role for Central Receiving
				}
				//WI275 ends here


				String crcombasedrole = var + "_" + sapSource +"_"+ country + "_" + company;
				Role crcombasedrole1 = Role.getRole(crcombasedrole);
		  		Log.customer.debug("crcombasedrole1: " + crcombasedrole1);
		  		if((crcombasedrole1 != null) && (crcombasedrole1.getActive()))
				{
	 				return crcombasedrole1;
				}


				String crcountrybasedrole = var + "_" + sapSource +"_"+ country;
				Role crcountrybasedrole1 = Role.getRole(crcountrybasedrole);
		 		Log.customer.debug("crcountrybasedrole1: " + crcountrybasedrole1);
		  		if((crcountrybasedrole1 != null) && (crcountrybasedrole1.getActive()))
				{
	 				return crcountrybasedrole1;
				}

				String crsapsourcebasedrole  = var + "_" + sapSource;
				Role crsapsourcebasedrole1 = Role.getRole(crsapsourcebasedrole);
		 		Log.customer.debug("crsapsourcebasedrole1: " + crsapsourcebasedrole1);
		  		if((crsapsourcebasedrole1 != null) && (crsapsourcebasedrole1.getActive()))
				{
	 				return crsapsourcebasedrole1;
				}

		  		// If role is not found then return null
		  		return null;

			}
			return null;
		}
		return null;
	}

	// Added by Majid for User Profile Role
		public static Role getUserProfileRole(Approvable r,String var)
		{

				String shipToID = null;
				String companyCodeID = null;
				String countryID = null;
				String sapSourceID = null;
				String roleSuffix = null;
				String all = "ALL";

				Log.customer.debug("getUserProfileRole : r: " + r);
				Log.customer.debug("getUserProfileRole : var: " + var);

				if (r instanceof UserProfile)
				{
				UserProfile	userProfile = (UserProfile) r;
				Log.customer.debug("getUserProfileRole : userProfile: " + userProfile.getUniqueName());

				roleSuffix = (String)userProfile.getDetailsFieldValue("PurchaseOrg.PurchaseOrgType");
				Log.customer.debug("getUserProfileRole : roleSuffix: " + roleSuffix);

				// Get UserProfile's ShipTo and Country
				if(userProfile.getDetailsFieldValue("ShipTo")!=null)
				{
					shipToID = (String)userProfile.getDetailsFieldValue("ShipTo.UniqueName");
					Log.customer.debug("getUserProfileRole : shipToID: " + shipToID);
					/**
					Not decided to have Country from ShipTo or Registered Address Country
					if(userProfile.getDetailsFieldValue("ShipTo.Country")!=null)
					{
						countryID = (String)userProfile.getDetailsFieldValue("ShipTo.Country.UniqueName");
						Log.customer.debug("getUserProfileRole : countryID: " + countryID);
					} **/
				}

				if(userProfile.getDetailsFieldValue("CompanyCode")!=null)
				{
					companyCodeID = (String)userProfile.getDetailsFieldValue("CompanyCode.UniqueName");
					Log.customer.debug("getUserProfileRole : companyCodeID: " + companyCodeID);

					if(userProfile.getDetailsFieldValue("CompanyCode.RegisteredAddress")!=null && userProfile.getDetailsFieldValue("CompanyCode.RegisteredAddress.Country")!=null )
					{
						countryID = (String) userProfile.getDetailsFieldValue("CompanyCode.RegisteredAddress.Country.UniqueName");
						Log.customer.debug("getUserProfileRole : countryID: " + countryID);
					}

					sapSourceID = (String)userProfile.getDetailsFieldValue("CompanyCode.SAPSource");
					Log.customer.debug("getUserProfileRole : sapSourceID: " + sapSourceID);
				}


				// Specific ShipTo's Role :
				String upShipToBasedRoleStr = var + "_" + sapSourceID +"_"+ countryID + "_" + companyCodeID +"_" + shipToID + "_" +roleSuffix;
				Log.customer.debug("upShipToBasedRole: " +upShipToBasedRoleStr );
				Role upShipToBasedRole = Role.getRole(upShipToBasedRoleStr);
				if((upShipToBasedRole != null) && (upShipToBasedRole.getActive()))
				{
					return upShipToBasedRole;
				}

				// Generic ShipTo's Role
				String upShipToAllBasedRoleStr = var + "_" + sapSourceID +"_"+ countryID + "_" + companyCodeID + "_" + all + "_" +roleSuffix;
				Log.customer.debug("upShipToAllBasedRoleStr: " +upShipToAllBasedRoleStr );
				Role upShipToAllBasedRole = Role.getRole(upShipToAllBasedRoleStr);
				if((upShipToAllBasedRole != null) && (upShipToAllBasedRole.getActive()))
				{
					return upShipToAllBasedRole;
				}



				// Specific CompanyCode's Role :
				String upCompanyCodeBasedRoleStr = var + "_" + sapSourceID +"_"+ countryID + "_" + companyCodeID +"_" +roleSuffix;
				Log.customer.debug("upCompanyCodeBasedRoleStr: " +upCompanyCodeBasedRoleStr );
				Role upCompanyCodeBasedRole = Role.getRole(upCompanyCodeBasedRoleStr);
				if((upCompanyCodeBasedRole != null) && (upCompanyCodeBasedRole.getActive()))
				{
					return upCompanyCodeBasedRole;
				}


				// Generic CompanyCode's Role
				String upCompanyCodeAllBasedRoleStr = var + "_" + sapSourceID +"_"+ countryID + "_" + all + "_" +roleSuffix;
				Log.customer.debug("upCompanyCodeAllBasedRoleStr: " +upCompanyCodeAllBasedRoleStr );
				Role upCompanyCodeAllBasedRole = Role.getRole(upCompanyCodeAllBasedRoleStr);
				if((upCompanyCodeAllBasedRole != null) && (upCompanyCodeAllBasedRole.getActive()))
				{
					return upCompanyCodeAllBasedRole;
				}



				// Specific Country's Role :
				String upCountryBasedRoleStr = var + "_" + sapSourceID + "_" + countryID + "_" +roleSuffix;
				Log.customer.debug("upCountryBasedRoleStr: " +upCountryBasedRoleStr );
				Role upCountryBasedRole = Role.getRole(upCountryBasedRoleStr);
				if((upCountryBasedRole != null) && (upCountryBasedRole.getActive()))
				{
					return upCountryBasedRole;
				}


				// Generic Country's Role
				String upCountryAllBasedRoleStr = var + "_" + sapSourceID +"_"+ all + "_" +roleSuffix;
				Log.customer.debug("upCountryAllBasedRoleStr: " +upCountryAllBasedRoleStr );
				Role upCountryAllBasedRole = Role.getRole(upCountryAllBasedRoleStr);
				if((upCountryAllBasedRole != null) && (upCountryAllBasedRole.getActive()))
				{
					return upCountryAllBasedRole;
				}



				//	Specific Source's Role :
				String upSourceBasedRoleStr = var + "_" + sapSourceID + "_" +roleSuffix;
				Log.customer.debug("upSourceBasedRoleStr: " +upSourceBasedRoleStr );
				Role upSourceBasedRole = Role.getRole(upSourceBasedRoleStr);
				if((upSourceBasedRole != null) && (upSourceBasedRole.getActive()))
				{
					return upSourceBasedRole;
				}


				// Generic Source's Role
				String upSourceAllBasedRoleStr = var + "_" + all + "_" +roleSuffix;
				Log.customer.debug("upSourceAllBasedRoleStr: " +upSourceAllBasedRoleStr );
				Role upSourceAllBasedRole = Role.getRole(upSourceAllBasedRoleStr);
				if((upSourceAllBasedRole != null) && (upSourceAllBasedRole.getActive()))
				{
					return upSourceAllBasedRole;
				}
				// If there is no Role found into the system then return null
				return null;
				}

				// If there is no Role found for above instances then return null
				return null;
	}
	//Added By nag for Cascade Capital

	public static List sortLevelsByAmount(List levels, Partition partition) {

				Currency defaultCurrency = Currency.getDefaultCurrency(partition);
				Object[] alArray = levels.toArray();

				// sort levels by amount
				int s = Array.getLength(alArray);

				for (int i = 0; i < s - 1; i++) {
				for (int j = 0; j < s - i - 1; j++) {
				BaseObject lvl = (BaseObject) alArray[j];
				Money lvlAmt = (Money) lvl.getFieldValue("Limit");
				Money lvlAmtInBase = lvlAmt.convertToCurrency(defaultCurrency);

				BaseObject lvl2 = (BaseObject) alArray[j + 1];
				Money lvl2Amt = (Money) lvl2.getFieldValue("Limit");
				Money lvl2AmtInBase = lvl2Amt.convertToCurrency(defaultCurrency);

				if (lvlAmtInBase.compareTo(lvl2AmtInBase) > 0) {
				Object temp = alArray[j];

				alArray[j] = alArray[j + 1];
				alArray[j + 1] = temp;
                }
            }
        }

        return Arrays.asList(alArray);
    }
	// passing Plant and Buyer Assgn in isCategoryManagerRequired and getCategoryManager methods for PCL changes
	public static boolean isCategoryManagerRequired(String CCToSearch,String CompanyCode, Money CCAmtToCompare,String Plant, String BuyerAssgn)
 	{
 		String filename = "config/variants/SAP/data/CategoryManagers.csv";
 		//ArrayList valuelist = new ArrayList();
	     Log.customer.debug("CatCommonUtil *** getCategoryManager inside 1st if" + Plant);
		Log.customer.debug("CatCommonUtil *** getCategoryManager inside 1st if" + BuyerAssgn);

 		if (!StringUtil.nullOrEmptyOrBlankString(filename) && CCToSearch != null && CompanyCode != null && CCAmtToCompare != null) {
 			File file = new File(filename);
 			if (file != null) {
 				try {
 					BufferedReader br = new BufferedReader(new FileReader(file));
 		 			String line = null;
  					while ((line = br.readLine())!= null) {
 						List values = parseParamString(line);
 						if (values.size() > 1 && ((String)values.get(0)).equals(CCToSearch)  && ((String)values.get(1)).equals(CompanyCode) && (String)values.get(2)!=null && (String)values.get(3)!=null) {
 							String limit1 = (String)values.get(2);
 							String currency1 = (String)values.get(3);
 							Money limitAmt =  new Money(new BigDecimal(limit1), Currency.getCurrencyFromUniqueName(currency1));
 							if (CCAmtToCompare.compareTo(limitAmt)>=0){
							  if((BuyerAssgn.equals("Plant")) && (Plant!=null)){
							   Log.customer.debug("CatCommonUtil *** getCategoryManager Method and inside MACH and Buyer Assgn is Plant" );
								 String roleuniquename = (String)values.get(4);
								 int lenrole = roleuniquename.length();
								// Log.customer.debug("CatCommonUtil *** getCategoryManager Method length of role :" +lenrole);
								 String Plantinrole = roleuniquename.substring((lenrole-4),lenrole);
								 Log.customer.debug("CatCommonUtil *** getCategoryManager Method Plantinrole :" +Plantinrole);
								   if(Plantinrole.equals(Plant)){
								   Log.customer.debug("CatCommonUtil *** getCategoryManager plants equals :");

 								return true;
 							}
						 }
 								else if (!(BuyerAssgn.equals("Plant"))){
								        return true;
 							          }
							}

 							//valuelist.add(values.get(1));
 						}
 					}
 					br.close();
 				}
 				catch (IOException e) {
 					Log.customer.debug("%s *** IOException: %s", ClassName, e);
 				}
 			}
 		}
 		return false;
 	}
		public static String getCategoryManager(String CCToSearch,String CompanyCode, Money CCAmtToCompare,String Plant, String BuyerAssgn)
 	{
 		String filename = "config/variants/SAP/data/CategoryManagers.csv";
 		//ArrayList valuelist = new ArrayList();
 		if (!StringUtil.nullOrEmptyOrBlankString(filename) && CCToSearch != null && CompanyCode != null && CCAmtToCompare != null) {
	/*	Log.customer.debug("CatCommonUtil *** getCategoryManager inside 1st if" );
		Log.customer.debug("CatCommonUtil *** getCategoryManager inside 1st if" + CCToSearch);
		Log.customer.debug("CatCommonUtil *** getCategoryManager inside 1st if" + CompanyCode);
		Log.customer.debug("CatCommonUtil *** getCategoryManager inside 1st if" + CCAmtToCompare);
		Log.customer.debug("CatCommonUtil *** getCategoryManager inside 1st if" + Plant);
		Log.customer.debug("CatCommonUtil *** getCategoryManager inside 1st if" + BuyerAssgn); */

 			File file = new File(filename);
 			if (file != null) {
				Log.customer.debug("CatCommonUtil *** getCategoryManager file is not null" );
 				try {
 					BufferedReader br = new BufferedReader(new FileReader(file));
 		 			String line = null;
  					while ((line = br.readLine())!= null) {
 						List values = parseParamString(line);
 						if (values.size() > 1 && ((String)values.get(0)).equals(CCToSearch)  && ((String)values.get(1)).equals(CompanyCode) && (String)values.get(2)!=null && (String)values.get(3)!=null && (String)values.get(4)!=null) {
 							String limit1 = (String)values.get(2);
 							String currency1 = (String)values.get(3);
 							Money limitAmt =  new Money(new BigDecimal(limit1), Currency.getCurrencyFromUniqueName(currency1));
 							if (CCAmtToCompare.compareTo(limitAmt)>=0){
							Log.customer.debug("CatCommonUtil *** getCategoryManager always true" );
							   if((BuyerAssgn.equals("Plant")) && (Plant!=null)){
								Log.customer.debug("CatCommonUtil *** getCategoryManager Method and inside MACH and Buyer Assgn is Plant" );
								 String roleuniquename = (String)values.get(4);
								 int lenrole = roleuniquename.length();
								// Log.customer.debug("CatCommonUtil *** getCategoryManager Method length of role :" +lenrole);
								 String Plantinrole = roleuniquename.substring((lenrole-4),lenrole);
								 Log.customer.debug("CatCommonUtil *** getCategoryManager Method Plantinrole :" +Plantinrole);
								   if(Plantinrole.equals(Plant)){
								   Log.customer.debug("CatCommonUtil *** getCategoryManager plants equals :");
								    return (String)values.get(4);
								   }
								 }
							     else

 								return (String)values.get(4);
 							}
 							//valuelist.add(values.get(1));
 						}
 					}
 					br.close();
 				}
 				catch (IOException e) {
 					Log.customer.debug("%s *** IOException: %s", ClassName, e);
 				}
 			}
 		}
 		return null;
 	}
// code changes for PCL completed.
//Start of Issue 255-Read Data from File
		public static ArrayList<String> readDataFromFile(String filename)
	    {
            Log.customer.debug("Entering the method readDataFromFile in class CatCommonUtil ");

	        ArrayList<String> valueList = null;
	        if(!StringUtil.nullOrEmptyOrBlankString(filename))
	        {
	            Log.customer.debug("Inside if in readDataFromFile in class CatCommonUtil ");

	            File file = new File(filename);
	            Log.customer.debug("%s *** file: %s", "CatCommonUtil", file);
	            if(file != null)
	                try
	                {
	                    BufferedReader br = new BufferedReader(new FileReader(file));
	                    Log.customer.debug("%s *** br: %s", "CatCommonUtil", br);
	                    String line = null;
	                    valueList = new ArrayList<String>();
	                    while((line = br.readLine()) != null)
	                    {
	                        valueList.add(line.toString());
	                    }
	                    Log.customer.debug("CatCommonUtil *** valuelist.size(): " + valueList.size());
	                    br.close();
	                }
	                catch(IOException e)
	                {
	                    Log.customer.debug("CatCommonUtil *** IOException: %s", "CatCommonUtil", e);
	                }
	        }
            Log.customer.debug("Exit from the method readDataFromFile in class CatCommonUtil ");

	        return valueList;
	    }
// validation of value taken from Approvable and file
		public static Boolean checkValueIsAvailable(String valueFromApprovable,ArrayList<String> valueFromFile) {
            Log.customer.debug("Entering the method checkValueIsAvailable in class CatCommonUtil ");

			Boolean flag = false;
		     if(valueFromFile != null)
	         {
	             int size = valueFromFile.size();
	             Log.customer.debug("**** size of file in the method checkValueIsAvailable of classname CatCommonUtil (before): " + size);
	            for(int i= 0;i<size;i++)
	            {
	                 String value = (String)valueFromFile.get(i);
		             Log.customer.debug("**** get value from file in the method checkValueIsAvailable of classname CatCommonUtil (after): " + value);
	                 if(valueFromApprovable.equals(value))
	                 {
	    	             Log.customer.debug("**** inside if validation for the method checkValueIsAvailable of classname CatCommonUtil (before): " + valueFromApprovable);

	                	 flag = true;
	                     break;
	                 }
	             }
	         }
	            Log.customer.debug("Exit from the method checkValueIsAvailable in class CatCommonUtil ");

			return flag;
		}
// End of Issue 255
}
