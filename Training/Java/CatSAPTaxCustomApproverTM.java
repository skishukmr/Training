 /*
 	Author: Archana
 	This class will get triggered when IP Manager or MSC Admin is added to the PR, will read all the lineItems
 	  ans creates request XML file then makes call to vertex webservice and receives the response and parse that
 	  response file.
    Change History
 	#	Change By		Change Date		Description
 	=============================================================================================
 	1	Archana 		27/12/2011 		Removed the hyphen from the postal codes incurred during the request phase
 	2   IBM Parita Shah 22/08/2013     	Q4 2013 - RSD 111 - FDD 4.0/TDD1.2 Reason Code functionality to be enabled for MACH1
 */

package config.java.customapprover.sap;

/*import TaxManagerCode.ApprovalRequest;
 import TaxManagerCode.Partition;
 import TaxManagerCode.ValueSource;*/
import ariba.contract.core.ContractRequestLineItem;
import ariba.approvable.core.*;
import config.java.common.CatCommonUtil;
import ariba.contract.core.ContractRequest;
import ariba.tax.core.TaxCode;
import ariba.base.core.BaseVector;
import ariba.base.core.Base;
import ariba.base.core.aql.*;
import ariba.approvable.core.Comment;
import ariba.base.core.ClusterRoot;
import ariba.procure.core.ProcureLineItem; //import ariba.procure.core.ReqLineItem;
import ariba.base.fields.ValueSource;
import ariba.procure.core.ProcureLineItemCollection;
import ariba.user.core.Role;
import ariba.util.core.Date;
import ariba.base.core.BaseObject;
import ariba.user.core.User;
import ariba.base.core.LongString;
import ariba.util.core.*;
import ariba.util.log.Log;
import ariba.common.core.SplitAccounting;
import ariba.common.core.SplitAccountingCollection;
import ariba.common.core.Address;
import ariba.basic.core.Money;
import ariba.basic.core.Currency;
import ariba.purchasing.core.Requisition;
import java.util.concurrent.TimeoutException;
import org.apache.axis.AxisFault;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.rpc.ServiceException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import java.math.BigDecimal;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import java.io.*; //import java.util.Date;
import java.text.SimpleDateFormat;
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.axis.AxisFault;
import org.w3c.dom.*;
import org.xml.sax.SAXException;
import ariba.base.core.Partition;

public class CatSAPTaxCustomApproverTM extends CustomApproverDelegateAdapter {
	private static final String className = "CatSAPTaxCustomApproverTM";

	public static void notifyApprovalRequiredTM(ApprovalRequest arReq,
			ValueSource valuesource) {
		Log.customer.debug("%s **** IN notifyApprovalRequiredTM() ****",
				"CatTaxCustomApproverTM");

		Requisition ar = (Requisition) valuesource;
		Approvable lic;
		lic = ar;
		Partition partition = null;
		partition = ar.getPartition();
		User aribasys = User.getAribaSystemUser(partition);

		Date date = new Date();
		ProcureLineItem pli = null;
		SplitAccounting sa = null;
		String accountName = "";
		String costCenter = "";
		String root = "";
		String loginText = "";
		String data = "";
		String data1 = "";
		String submitDate = "";
		String docNumber = "";
		User u = null;
		String uname = "";
		String compCode = "";
		String companyCode = "";
		String division = "";
		String numberInCollection = "";
		String incoTerms1 = "";
		List splitAccountings = null;
		String city1 = "";
		String state = "";
		String postalCode = "";
		String country = "";
		String supplier = "";
		String flexibleCodeField2 = "";
		String respFile = null;
		SimpleDateFormat dateformatYYYYMMDD = new SimpleDateFormat("yyyy-MM-dd");

		if (lic instanceof ProcureLineItemCollection) {
			Log.customer.debug("%s **** Inside first if %s",
					"CatTaxCustomApproverTM", lic);
			ProcureLineItemCollection plic = (ProcureLineItemCollection) lic;
			BaseVector lineItems = null;
			if (plic != null) {
				lineItems = plic.getLineItems();
			}
			int count = lineItems.size();
			Log.customer
					.debug(
							"%s **** Multiple Line Items CatTaxCustomApproverTM",
							count);
			Log.customer.debug("Inside createXMLFile() ***");

			try {
				root = "VertexEnvelope";
				DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory
						.newInstance();
				documentBuilderFactory.setNamespaceAware(true); // To make
																// Namespace
																// aware
				DocumentBuilder documentBuilder = documentBuilderFactory
						.newDocumentBuilder();
				Document document = documentBuilder.newDocument();
				Element rootElement = document.createElement(root);
				// Added both the namespaces
				String rootElementNS = ResourceService.getString("cat.ws.util",
						"rootElementNS");
				// String rootElementNS1 =
				// ResourceService.getString("cat.ws.util","rootElementNS1");
				// Added both the namespaces
				rootElement.setAttributeNS(rootElementNS, "xmlns",
						"urn:vertexinc:o-series:tps:6:0");
				rootElement.setAttributeNS(rootElementNS, "xmlns:xsi",
						"http://www.w3.org/2001/XMLSchema-instance");
				document.appendChild(rootElement);

				String userName = (String) lic
						.getDottedFieldValue("CompanyCode.VertexLoginUID");
				Log.customer.debug("Custom Approver userName : " + userName);
				String passWord = (String) lic
						.getDottedFieldValue("CompanyCode.VertexLoginPWD");
				Log.customer.debug("Custom Approver passWord :" + passWord);

				loginText = "Login"; // For Login
				Element login = document.createElement(loginText);
				rootElement.appendChild(login);

				String element = "UserName"; // Login User Name
				// data = userName;
				Element em = document.createElement(element);
				em.appendChild(document.createTextNode(userName));
				login.appendChild(em);

				String element1 = "Password"; // Login Password
				// data1 = passWord;
				Element em1 = document.createElement(element1);
				em1.appendChild(document.createTextNode(passWord));
				login.appendChild(em1);

				submitDate = dateformatYYYYMMDD.format(lic.getSubmitDate());
				Log.customer.debug(
						"%s **** Purchase Requistion submit date: %s",
						"CatTaxCustomApproverTM", submitDate);

				docNumber = lic.getUniqueName();
				Log.customer.debug(
						"%s **** Purchase Requistion Doc Number: %s",
						"CatTaxCustomApproverTM", docNumber);

				String ccodeMattaxvalue = (String) lic
						.getDottedFieldValue("CompanyCode.DefaultTaxCodeForMaterial.SAPTaxCode");
				if (ccodeMattaxvalue == null) {
					ccodeMattaxvalue = "";
				} else {
					ccodeMattaxvalue = ccodeMattaxvalue;
				}
				Log.customer
						.debug("Custom Approver Material tax in companycode"
								+ ccodeMattaxvalue);
				String ccodeServicetaxvalue = (String) lic
						.getDottedFieldValue("CompanyCode.DefaultTaxCodeForService.SAPTaxCode");
				if (ccodeServicetaxvalue == null) {
					ccodeServicetaxvalue = "";
				} else {
					ccodeServicetaxvalue = ccodeServicetaxvalue;
				}
				Log.customer
						.debug("Custom Approver Serverice tax in companycode"
								+ ccodeServicetaxvalue);

				String PORText = "InvoiceVerificationRequest";
				String strDocumentDate = "documentDate";
				String strRetAsstdPrmtr = "returnAssistedParametersIndicator";
				String strTranscType = "transactionType";
				String strDocNumber = "documentNumber";
				String strFlag = "true";
				String strPurchase = "PURCHASE";
				Attr attr1 = document.createAttribute(strDocumentDate);
				Attr attr2 = document.createAttribute(strRetAsstdPrmtr);
				Attr attr3 = document.createAttribute(strTranscType);
				Attr attr4 = document.createAttribute(strDocNumber);
				attr1.setNodeValue(submitDate);
				attr4.setNodeValue(docNumber);
				attr2.setNodeValue(strFlag);
				attr3.setNodeValue(strPurchase);
				Element POR = document.createElement(PORText);
				POR.setAttributeNode(attr1);
				POR.setAttributeNode(attr2);
				POR.setAttributeNode(attr3);
				POR.setAttributeNode(attr4);

				// Buyer Tag
				final String BuyerText = "Buyer";
				Element Buyer = document.createElement(BuyerText);
				POR.appendChild(Buyer);

				// Company Tag
				final String CompText = "Company";
				Element Comp = document.createElement(CompText);
				final String CompCode = "0001";
				Comp.appendChild(document.createTextNode(CompCode));
				Buyer.appendChild(Comp);

				// Division Tag
				final String DivText = "Division";
				Element Division = document.createElement(DivText);
				String DivisionCode = (String) lic
						.getDottedFieldValue("CompanyCode.UniqueName");
				if (DivisionCode == null) {
					DivisionCode = "";
				} else {
					DivisionCode = DivisionCode;
				}
				Log.customer.debug("%s **** Company Code: %s",
						"CatTaxCustomApproverTM", DivisionCode);
				Division.appendChild(document.createTextNode(DivisionCode));
				Buyer.appendChild(Division);

				rootElement.appendChild(POR);

				for (int j = 0; j < count; j++) {
					pli = (ProcureLineItem) lineItems.get(j);
					Log.customer.debug("%s **** Print pli %s",
							"CatTaxCustomApproverTM");
					// Printing data from lineitem
					u = lic.getRequester();

					uname = (String) u.getFieldValue("UniqueName");
					uname = (uname == null) ? "" : uname;
					Log.customer.debug("%s **** User Details uname: %s",
							"CatTaxCustomApproverTM", uname);

					String sapCostCenter = (String) u
							.getFieldValue("SAPCostCenter");
					sapCostCenter = (sapCostCenter == null) ? ""
							: sapCostCenter;
					Log.customer.debug(
							"%s **** User Details SAPCostCenter : %s",
							className, sapCostCenter);

					compCode = (String) u.getFieldValue("CompanyCode");
					compCode = (compCode == null) ? "" : compCode;
					Log.customer.debug("%s **** User Details Comp Code: %s",
							"CatTaxCustomApproverTM", compCode);

					companyCode = (String) lic
							.getDottedFieldValue("CompanyCode.UniqueName");
					companyCode = (companyCode == null) ? "" : companyCode;
					Log.customer.debug("%s **** Company Code: %s",
							"CatTaxCustomApproverTM", companyCode);

					division = (String) u.getFieldValue("Division");
					division = (division == null) ? "" : division;
					Log.customer.debug("%s **** Division: %s",
							"CatTaxCustomApproverTM", division);

					numberInCollection = Integer.toString(pli
							.getNumberInCollection());
					numberInCollection = (numberInCollection == null) ? ""
							: numberInCollection;
					Log.customer.debug("%s **** Number in Collection: %s",
							"CatTaxCustomApproverTM", numberInCollection);

					incoTerms1 = (String) pli
							.getDottedFieldValue("IncoTerms1.UniqueName");
					incoTerms1 = (incoTerms1 == null) ? "" : incoTerms1;
					Log.customer.debug("%s **** Incoterms value: %s",
							"CatTaxCustomApproverTM", incoTerms1);

					Log.customer
							.debug(" CatTaxCustomApproverTM : Reading costCenter and generalLedgerAccount ");
					SplitAccountingCollection sac = (SplitAccountingCollection) pli
							.getDottedFieldValue("Accountings");
					splitAccountings = (List) sac
							.getDottedFieldValue("SplitAccountings");
					Log.customer
							.debug(" CatTaxCustomApprover : splitAccountings "
									+ splitAccountings);

					if (splitAccountings != null) {
						sa = (SplitAccounting) splitAccountings.get(0);
						Log.customer
								.debug(" CatTaxCustomApproverTM : SplitAccounting sa "
										+ sa);
						costCenter = (String) sa
								.getDottedFieldValue("CostCenterText");
						Log.customer
								.debug(" CatTaxCustomApproverTM : SplitAccounting cost center dotted fieldvalue "
										+ costCenter);
						// accountName =//
						// (String)sa.getDottedFieldValue("Account.UniqueName");
						accountName = (String) sa
								.getDottedFieldValue("GeneralLedgerText");
						Log.customer
								.debug(" CatTaxCustomApproverTM : SplitAccounting account unique name dotted fieldvalue "
										+ accountName);
					}

					// Reading city, main division, postalcode and country -
					// buyer details needs to check with eric.

					Log.customer.debug(" CatTaxCustomApproverTM :  cost center"
							+ costCenter);
					Log.customer.debug(" CatTaxCustomApproverTM : account  "
							+ accountName);
					if (accountName == null) {
						accountName = "";
					} else {
						accountName = accountName;
					}
					if (costCenter == null) {
						costCenter = sapCostCenter;
					} else {
						costCenter = costCenter;
					}
					if (incoTerms1 == null) {
						incoTerms1 = "";
					} else {
						incoTerms1 = incoTerms1;
					}

					Address shipTo1 = (Address) pli
							.getDottedFieldValue("ShipTo");
					Log.customer.debug(" CatTaxCustomApproverTM : ShipTo1 "
							+ shipTo1);

					city1 = (String) shipTo1.getFieldValue("City");
					Log.customer.debug(" CatTaxCustomApproverTM : City1 "
							+ city1);

					if (city1 == null) {
						city1 = "";
					} else {
						city1 = city1;
					}

					state = (String) pli.getDottedFieldValue("ShipTo.State");
					Log.customer.debug(" CatTaxCustomApproverTM : State "
							+ state);

					if (state == null) {
						state = "none";
					} else {
						state = state;
					}

					postalCode = (String) pli
							.getDottedFieldValue("ShipTo.PostalCode");
					Log.customer.debug(" CatTaxCustomApproverTM : Postal Code "
							+ postalCode);

					if (postalCode == null) {
						postalCode = "";
					} else {
						postalCode = postalCode;
					}
					postalCode = postalCode.replaceAll("-", "");
					country = (String) pli
							.getDottedFieldValue("ShipTo.Country.UniqueName");
					Log.customer.debug(" CatTaxCustomApproverTM : Country "
							+ country);

					if (country == null) {
						country = "";
					} else {
						country = country;
					}

					// Supplier Information - reading from Ariba - Archana
					// Panchal.
					supplier = (String) pli
							.getDottedFieldValue("Supplier.UniqueName");
					Log.customer.debug(" CatTaxCustomApproverTM : Supplier "
							+ supplier);

					if (supplier == null) {
						supplier = "";
					} else {
						supplier = supplier;
					}

					String supplierCity = (String) pli
							.getDottedFieldValue("SupplierLocation.City");
					Log.customer
							.debug(" CatTaxCustomApproverTM : supplierCity "
									+ supplierCity);

					if (supplierCity == null) {
						supplierCity = "";
					} else {
						supplierCity = supplierCity;
					}

					String supplierState = (String) pli
							.getDottedFieldValue("SupplierLocation.State");
					Log.customer.debug(" CatTaxCustomApproverTM : State "
							+ supplierState);

					if (supplierState == null) {
						supplierState = "";
					} else {
						supplierState = supplierState;
					}

					String supplierPostalCode = (String) pli
							.getDottedFieldValue("SupplierLocation.PostalCode");
					Log.customer.debug(" CatTaxCustomApproverTM : Postal Code "
							+ supplierPostalCode);

					if (supplierPostalCode == null) {
						supplierPostalCode = "";
					} else {
						supplierPostalCode = supplierPostalCode;
					}

					String suplierCountry = (String) pli
							.getDottedFieldValue("SupplierLocation.Country.UniqueName");
					Log.customer.debug(" CatTaxCustomApproverTM : Country "
							+ suplierCountry);

					if (suplierCountry == null) {
						suplierCountry = "";
					} else {
						suplierCountry = suplierCountry;
					}

					// String commodityCode = (String)
					// pli.getDottedFieldValue("CommodityCode.UniqueName");
					String commodityCode = (String) pli
							.getDottedFieldValue("Description.CommonCommodityCode.UniqueName");
					Log.customer
							.debug(" CatTaxCustomApproverTM : commodityCode "
									+ commodityCode);

					// Flexible Fields....
					flexibleCodeField2 = (String) pli
							.getDottedFieldValue("TaxCode.SAPTaxCode");
					Log.customer
							.debug(" CatTaxCustomApproverTM : flexibleCodeField2 "
									+ flexibleCodeField2);

					if (flexibleCodeField2 == null) {
						flexibleCodeField2 = "";
					} else {
						flexibleCodeField2 = flexibleCodeField2;
					}

					String flexibleCodeField1 = accountName;
					Log.customer
							.debug(" CatTaxCustomApproverTM : flexibleCodeField1 "
									+ flexibleCodeField1);

					if (flexibleCodeField1 == null) {
						flexibleCodeField1 = "";
					} else {
						flexibleCodeField1 = flexibleCodeField1;
					}

					String flexibleCodeField3 = (String) pli
							.getDottedFieldValue("LineItemType");
					if (flexibleCodeField3.contains("TQM")) {
						flexibleCodeField3 = "M";
					}
					if (flexibleCodeField3.contains("TQB")) {
						flexibleCodeField3 = "B";
					}
					if (flexibleCodeField3.contains("TQC")) {
						flexibleCodeField3 = "C";
					}
					if (flexibleCodeField3.contains("TQS")) {
						flexibleCodeField3 = "S";
					}
					Log.customer
							.debug(" CatTaxCustomApproverTM : flexibleCodeField3 "
									+ flexibleCodeField3);

					String flexibleCodeField5 = state;
					Log.customer
							.debug(" CatTaxCustomApproverTM : flexibleCodeField5 "
									+ flexibleCodeField5);

					// String flexibleCodeField6 = (String)
					// pli.getDottedFieldValue("TaxCode.SAPTaxCode");
					// Log.customer.debug(" CatTaxCustomApprover : flexibleCodeField2 "
					// + flexibleCodeField6);

					String flexibleCodeField25 = suplierCountry;
					Log.customer
							.debug(" CatTaxCustomApproverTM : flexibleCodeField25 "
									+ flexibleCodeField25);

					// Yet to read flexibleCodeField 6.

					String mannerOfUse = (String) pli
							.getDottedFieldValue("TaxUse.UniqueName");
					Log.customer
							.debug(" CatTaxCustomApproverTM : TaxUse.UniqueName "
									+ mannerOfUse);

					String quantity = ((BigDecimal) pli
							.getDottedFieldValue("Quantity")).toString();
					Log.customer.debug(" CatTaxCustomApproverTM : quantity "
							+ quantity);

					String amount = "";
					if (pli instanceof ariba.contract.core.ContractRequestLineItem) {
						Log.customer
								.debug(" Contract request line - using the headerlevel max amount");
						ContractRequest mar = (ContractRequest) pli
								.getLineItemCollection();
						amount = (mar.getMaxAmount() != null) ? mar
								.getMaxAmount().getAmount().toString()
								: (Constants.ZeroBigDecimal).toString();
						Log.customer.debug("%s : amount contract %s",
								className, amount);
					} else {
						amount = ((BigDecimal) pli
								.getDottedFieldValue("Amount.Amount"))
								.toString();
						Log.customer.debug("%s : amount else %s", className,
								amount);
					}

					if (flexibleCodeField3 == null) {
						flexibleCodeField3 = "";
					} else {
						flexibleCodeField3 = flexibleCodeField3;
					}

					if (flexibleCodeField5 == null) {
						flexibleCodeField5 = "";
					} else {
						flexibleCodeField5 = flexibleCodeField5;
					}

					if (commodityCode == null) {
						commodityCode = "";
					} else {
						commodityCode = commodityCode;
					}

					if (quantity == null) {
						quantity = "";
					} else {
						quantity = quantity;
					}

					if (mannerOfUse == null) {
						mannerOfUse = "";
					} else {
						mannerOfUse = mannerOfUse;
					}

					if (flexibleCodeField25 == null) {
						flexibleCodeField25 = "";
					} else {
						flexibleCodeField25 = flexibleCodeField25;
					}

					if (flexibleCodeField2.equals(ccodeMattaxvalue)
							|| flexibleCodeField2.equals(ccodeServicetaxvalue)) {
						// Generating XML..;
						Log.customer
								.debug(" CatTaxCustomApproverTM : in if - B1 then generate XML : ");
						String LineItemText = "LineItem";
						Element LineItem = document.createElement(LineItemText);

						String liattribute1 = "lineItemNumber";
						Attr liAttr1 = document.createAttribute(liattribute1);
						LineItem.setAttributeNode(liAttr1);
						liAttr1.setNodeValue(numberInCollection);
						Log.customer.debug("numberInCollection :"
								+ numberInCollection);

						String liattribute2 = "costCenter";
						Attr liAttr2 = document.createAttribute(liattribute2);
						LineItem.setAttributeNode(liAttr2);
						Log.customer
								.debug(" CatTaxCustomApproverTM : costCenter in xml "
										+ costCenter);
						liAttr2.setNodeValue(costCenter);
						// liAttr2.setNodeValue("");

						String liattribute3 = "generalLedgerAccount";
						Attr liAttr3 = document.createAttribute(liattribute3);
						LineItem.setAttributeNode(liAttr3);
						Log.customer
								.debug(" CatTaxCustomApproverTM : AccountName in xml "
										+ accountName);
						liAttr3.setNodeValue(accountName);
						// liAttr3.setNodeValue("");

						String liattribute4 = "deliveryTerm";
						Attr liAttr4 = document.createAttribute(liattribute4); //
						LineItem.setAttributeNode(liAttr4); //
						liAttr4.setNodeValue(incoTerms1);
						// liAttr4.setNodeValue("");

						Log.customer.debug("incoTerms1 :" + incoTerms1);

						String liattribute5 = "usageClass";
						Attr liAttr5 = document.createAttribute(liattribute5);
						LineItem.setAttributeNode(liAttr5);
						liAttr5.setNodeValue(mannerOfUse);
						Log.customer.debug("mannerOfUse :" + mannerOfUse);

						// Buyer Tag
						String BuyerText1 = "Buyer";
						Element Buyer1 = document.createElement(BuyerText1);
						LineItem.appendChild(Buyer1);

						/*
						 * // Company Tag String CompText = "Company"; Element
						 * Comp = document.createElement(CompText); String
						 * CompCode = "0001";
						 * Comp.appendChild(document.createTextNode(CompCode));
						 * Buyer.appendChild(Comp);
						 *
						 * // Division Tag String DivText = "Division"; Element
						 * Division = document.createElement(DivText); String
						 * DivisionCode = (String)
						 * lic.getDottedFieldValue("CompanyCode.UniqueName");
						 * Log.customer.debug("%s **** Company Code: %s",
						 * "CatTaxCustomApprover", DivisionCode);
						 * Division.appendChild
						 * (document.createTextNode(DivisionCode));
						 * Buyer.appendChild(Division);
						 */
						// Destination Tag
						String DestText = "Destination";
						Element destination = document.createElement(DestText);
						Buyer1.appendChild(destination);

						// City Tag
						String CityText = "City";
						Element city = document.createElement(CityText);
						String CityData = city1;
						city.appendChild(document.createTextNode(CityData));
						destination.appendChild(city);

						// Main Division Tag
						String MailDivisionText = "MainDivision";
						Element mainDivision = document
								.createElement(MailDivisionText);
						String mainDVdata = state;
						mainDivision.appendChild(document
								.createTextNode(mainDVdata));
						destination.appendChild(mainDivision);

						// Postal code Tag
						String postalCodeText = "PostalCode";
						Element PostalCode = document
								.createElement(postalCodeText);
						String PostalCodeData = postalCode;
						PostalCode.appendChild(document
								.createTextNode(PostalCodeData));
						destination.appendChild(PostalCode);

						// Country Tag
						String CountryText = "Country";
						Element Country = document.createElement(CountryText);
						String Countrydata = country;
						Country.appendChild(document
								.createTextNode(Countrydata));
						destination.appendChild(Country);

						String VendorText = "Vendor";
						Element vendor = document.createElement(VendorText);
						LineItem.appendChild(vendor);

						String VendorCode = "VendorCode";
						Element Vendor = document.createElement(VendorCode);
						String VenCode = (String) pli
								.getDottedFieldValue("Supplier.UniqueName");
						Vendor.appendChild(document.createTextNode(VenCode));
						vendor.appendChild(Vendor);
						Log.customer.debug("VenCode :" + VenCode);

						String AdministrativeOriginText = "AdministrativeOrigin";
						Element AdministrativeOrigin = document
								.createElement(AdministrativeOriginText);
						vendor.appendChild(AdministrativeOrigin);

						// City Tag
						String VenCityText = "City";
						Element Vencity = document.createElement(VenCityText);
						String VenCityData = (String) pli
								.getDottedFieldValue("SupplierLocation.City");
						Vencity.appendChild(document
								.createTextNode(VenCityData));
						AdministrativeOrigin.appendChild(Vencity);
						Log.customer.debug("VenCityData :" + VenCityData);

						// Main Division Tag
						String VenMailDivText = "MainDivision";
						Element VenmainDivision = document
								.createElement(VenMailDivText);
						String VenmainDVdata = (String) pli
								.getDottedFieldValue("SupplierLocation.State");
						VenmainDivision.appendChild(document
								.createTextNode(VenmainDVdata));
						AdministrativeOrigin.appendChild(VenmainDivision);

						// Postal code Tag
						String VenpostalCodeText = "PostalCode";
						Element VenPostalCode = document
								.createElement(VenpostalCodeText);
						String VenPostalCodeData = (String) pli
								.getDottedFieldValue("SupplierLocation.PostalCode");
						VenPostalCode.appendChild(document
								.createTextNode(VenPostalCodeData));
						AdministrativeOrigin.appendChild(VenPostalCode);

						// Country Tag
						String VenCountryText = "Country";
						Element VenCountry = document
								.createElement(VenCountryText);
						String VenCountrydata = (String) pli
								.getDottedFieldValue("SupplierLocation.Country.UniqueName");
						VenCountry.appendChild(document
								.createTextNode(VenCountrydata));
						AdministrativeOrigin.appendChild(VenCountry);

						String chargedTaxText = "ChargedTax";
						Element chargedTax = document
								.createElement(chargedTaxText);
						chargedTax.appendChild(document.createTextNode("0"));
						LineItem.appendChild(chargedTax);

						String PurchaseText = "Purchase";
						Element Purchase = document.createElement(PurchaseText);
						Purchase.appendChild(document
								.createTextNode(commodityCode));
						LineItem.appendChild(Purchase);

						String QuantityText = "Quantity";
						Element Quantity = document.createElement(QuantityText);
						Quantity.appendChild(document.createTextNode(quantity));
						LineItem.appendChild(Quantity);

						String ExtendedPriceText = "ExtendedPrice";
						Element extendedPrice = document
								.createElement(ExtendedPriceText);
						extendedPrice.appendChild(document
								.createTextNode(amount));
						LineItem.appendChild(extendedPrice);

						// FlexibleFields Tag
						String FlexibleFieldsText = "FlexibleFields";
						Element FlexibleFields = document
								.createElement(FlexibleFieldsText);
						LineItem.appendChild(FlexibleFields);

						// FlexibleCodeField tag

						String FlexibleCodeFieldText1 = "FlexibleCodeField";
						String flexcodeFieldAttribute1 = "fieldId";
						String flexcodeFieldAttributeData1 = "1";
						Attr flexcodeFieldAttr1 = document
								.createAttribute(flexcodeFieldAttribute1);
						flexcodeFieldAttr1
								.setNodeValue(flexcodeFieldAttributeData1);
						Element FlexibleCodeField1 = document
								.createElement(FlexibleCodeFieldText1);
						FlexibleCodeField1.setAttributeNode(flexcodeFieldAttr1);
						String FlexibleCodeFielddata1 = flexibleCodeField1;
						FlexibleCodeField1.appendChild(document
								.createTextNode(FlexibleCodeFielddata1));
						FlexibleFields.appendChild(FlexibleCodeField1);

						String FlexibleCodeFieldText2 = "FlexibleCodeField";
						String flexcodeFieldAttribute2 = "fieldId";
						String flexcodeFieldAttributeData2 = "2";
						Attr flexcodeFieldAttr2 = document
								.createAttribute(flexcodeFieldAttribute2);
						flexcodeFieldAttr2
								.setNodeValue(flexcodeFieldAttributeData2);
						Element FlexibleCodeField2 = document
								.createElement(FlexibleCodeFieldText2);
						FlexibleCodeField2.setAttributeNode(flexcodeFieldAttr2);
						String FlexibleCodeFielddata2 = flexibleCodeField2;
						FlexibleCodeField2.appendChild(document
								.createTextNode(FlexibleCodeFielddata2));
						FlexibleFields.appendChild(FlexibleCodeField2);

						String FlexibleCodeFieldText3 = "FlexibleCodeField";
						String flexcodeFieldAttribute3 = "fieldId";
						String flexcodeFieldAttributeData3 = "3";
						Attr flexcodeFieldAttr3 = document
								.createAttribute(flexcodeFieldAttribute3);
						flexcodeFieldAttr3
								.setNodeValue(flexcodeFieldAttributeData3);
						Element FlexibleCodeField3 = document
								.createElement(FlexibleCodeFieldText3);
						FlexibleCodeField3.setAttributeNode(flexcodeFieldAttr3);
						String FlexibleCodeFielddata3 = flexibleCodeField3;
						FlexibleCodeField3.appendChild(document
								.createTextNode(FlexibleCodeFielddata3));
						FlexibleFields.appendChild(FlexibleCodeField3);

						String FlexibleCodeFieldText5 = "FlexibleCodeField";
						String flexcodeFieldAttribute5 = "fieldId";
						String flexcodeFieldAttributeData5 = "5";
						Attr flexcodeFieldAttr5 = document
								.createAttribute(flexcodeFieldAttribute5);
						flexcodeFieldAttr5
								.setNodeValue(flexcodeFieldAttributeData5);
						Element FlexibleCodeField5 = document
								.createElement(FlexibleCodeFieldText5);
						FlexibleCodeField5.setAttributeNode(flexcodeFieldAttr5);
						String FlexibleCodeFielddata5 = flexibleCodeField5;
						FlexibleCodeField5.appendChild(document
								.createTextNode(FlexibleCodeFielddata5));
						FlexibleFields.appendChild(FlexibleCodeField5);

						String FlexibleCodeFieldText25 = "FlexibleCodeField";
						String flexcodeFieldAttribute25 = "fieldId";
						String flexcodeFieldAttributeData25 = "25";
						Attr flexcodeFieldAttr25 = document
								.createAttribute(flexcodeFieldAttribute25);
						flexcodeFieldAttr25
								.setNodeValue(flexcodeFieldAttributeData25);
						Element FlexibleCodeField25 = document
								.createElement(FlexibleCodeFieldText25);
						FlexibleCodeField25
								.setAttributeNode(flexcodeFieldAttr25);
						String FlexibleCodeFielddata25 = flexibleCodeField25;
						FlexibleCodeField25.appendChild(document
								.createTextNode(FlexibleCodeFielddata25));
						FlexibleFields.appendChild(FlexibleCodeField25);

						POR.appendChild(LineItem);
					}
				}
				Log.customer
						.debug("...VertexTaxWSCall *** flexibleCodeField2 checkin tax code for file generation... "
								+ flexibleCodeField2);
				if (flexibleCodeField2.equalsIgnoreCase("B1")) {
					TransformerFactory transformerFactory = TransformerFactory
							.newInstance();
					Transformer transformer = transformerFactory
							.newTransformer();
					DOMSource source = new DOMSource(document);
					String filePath = ResourceService.getString("cat.ws.util",
							"VertexFilePath");
					File file = new File(filePath + "VertexRequest_"
							+ docNumber + "_" + date.getTime() + ".xml");
					StreamResult result = new StreamResult(file);
					Log.customer
							.debug("...VertexTaxWSCall *** File content... "
									+ file);
					Log.customer
							.debug("...VertexTaxWSCall *** result and source content... "
									+ result + "," + source);
					Log.customer.debug("... Absolute path of the file ..."
							+ file.getAbsolutePath());
					if (transformer != null) {
						transformer.transform(source, result);
					} else {
						Log.customer.debug(
								"%s .....transformer is NULL ..... %s",
								className);
						return;
					}
					Log.customer
							.debug(".....Generated XML file successfully.....");
					// ****************************************************************/
					DocumentBuilderFactory dbf1 = DocumentBuilderFactory
							.newInstance();
					DocumentBuilder db1 = dbf1.newDocumentBuilder();
					Document doc1 = db1.parse(file);
					doc1.getDocumentElement().normalize();
					Log.customer.debug("Root element for request file"
							+ doc1.getDocumentElement().getNodeName());
					NodeList nodeList1 = doc1.getElementsByTagName("LineItem");
					int size = nodeList1.getLength();
					Log.customer.debug("size for request file" + size);

					if (size > 0) {
						Log.customer.debug("Before calling next class...: %s",
								"CatTaxCustomApproverTM", "");
						VertexTaxWSCall wscall = new VertexTaxWSCall();
						Log.customer.debug(
								"Before calling getVertexTaxResponse()...: %s",
								"CatTaxCustomApproverTM", "");
						try {
							respFile = wscall.getVertexTaxResponse(file,
									docNumber);
						}
						/***************************************************************************************/
						catch (AxisFault f) {
							// Determine type of fault (Client or Server)
							Log.customer.debug(" In Catch ......");
							String faultCode = f.getFaultCode().getLocalPart();
							String faultDetails = f.getFaultString();
							String faultString = f.getFaultString();
							Log.customer
									.debug(" In Catch ...... f.toString() :- "
											+ f.toString());
							Log.customer
									.debug(" In Catch ...... faultString:- "
											+ faultString);
							Log.customer.debug(" In Catch ...... faultCode:- "
									+ faultCode);
							Log.customer
									.debug(" In Catch ...... faultDetails:- "
											+ faultDetails);

							BaseVector comments = plic.getComments();
							int count1 = comments.size();
							Log.customer
									.debug(
											"%s **** Multiple comments CatTaxCustomApproverTM setting up comments",
											count1);
							Comment comment = new Comment(ar.getPartition());
							comment.setType(Comment.TypeGeneral);
							comment.setTitle("Failuire Reason");
							comment.setDate(new Date());
							comment.setUser(aribasys);
							comment.setExternalComment(false);
							comment.setParent(ar);
							String stcktrace = f.getStackTrace().toString();
							String failReason = "Reason For Failure: ";
							if (faultCode.equalsIgnoreCase("server")) {
								// This indicates that the Web Service is not in
								// a valid
								// state Processing should stop until the
								// problem is
								// resolved. A
								// "Server" fault is generated when a
								// VertexSystemException is thrown on the
								// Server.
								Log.customer
										.debug("web-service is not in valid state. ****************  Server");
								faultString = faultString
										.concat("\n web-service is not in valid state.");
								Log.customer.debug(" fault String :- "
										+ faultString);
								LongString commnetText = new LongString(
										faultString);
								comment.setText(commnetText);
								Log.customer.debug(faultDetails.toString());
								Log.customer.debug("Reason :- "
										+ f.getFaultReason());
								Log.customer.debug("message :- "
										+ f.getMessage());
								// Addition of Vertex Manager..............
								lic.setFieldValue("ProjectID", "F");
								LongString longstring = new LongString(
										faultString);
								// addCommentToPR(arReq,longstring,failReason,date,aribasys);
								if ("".equalsIgnoreCase(incoTerms1)) {
									addIPToPR(lic, arReq);
								} else {
									Log.customer.debug(
											"%s pli Vertex Manager:- %s",
											className, pli);
									addVertexManagerToPR(arReq, lic);
								}
							} else if (faultCode.equalsIgnoreCase("client")) {
								// A "Client" fault would indicate that the
								// request is
								// flawed but processing of additional requests
								// could
								// continue. A "Client"
								// fault is generated when a
								// VertexApplicationException
								// is thrown on the Server.
								Log.customer
										.debug("The XML request is invalid. Fix the request and resend.******** client");
								faultString = faultString
										.concat("\n The XML request is invalid. Fix the request and resend.");
								Log.customer.debug(" fault String :- "
										+ faultString);
								LongString commnetText = new LongString(
										faultString);
								comment.setText(commnetText);
								Log.customer.debug(faultDetails.toString());
								Log.customer.debug("Reason :- "
										+ f.getFaultReason());
								Log.customer.debug("message :- "
										+ f.getMessage());
								// addTaxManagerToPR(ar,lic);
								LongString longstring = new LongString(
										faultString);
								// addCommentToPR(arReq,longstring,failReason,date,aribasys);
								// Addition of tax manager................
								// addTaxManagerToPR(ar,lic);
								if (faultString.contains("User login failed:")) {
									addMSCAdminToPR(arReq, lic);
								} else {
									Log.customer.debug("%s pli :- %s",
											className, pli);
									// ReqLineItem rli = (ReqLineItem)pli;
									addIPToPR(lic, arReq);
								}

							}
							plic.getComments().add(comment);
							Log.customer
									.debug("%s ****  Completed Comments : - ");
							Log.customer
									.debug("%s ****  Completed Axis Fault : - ");
						}

						catch (IOException e) {
							// TODO Auto-generated catch block
							Log.customer.debug("In IO Exception");
							Log.customer.debug("in Exception" + e.getMessage());
							e.printStackTrace();
						} catch (NullPointerException e) {
							Log.customer.debug("In Null Exception");
							Log.customer.debug("in Exception" + e.getMessage());
						} catch (TimeoutException a) {
							Log.customer
									.debug("Inside TimeoutException . Fix the request and resend.");
							Log.customer.debug(a.getCause());
							Log.customer.debug(a.getMessage());
						} catch (Exception a) {
							Log.customer
									.debug("Inside Exception . Fix the request and resend.");
							Log.customer.debug(a.getCause());
							Log.customer.debug(a.getMessage());
						}
					}
				}
				/****************************************************************/
				Log.customer.debug(
						"After calling getVertexTaxResponse()...: %s",
						"CatTaxCustomApproverTM");
				/***************************************************************************************/
				Log.customer.debug(
						"After calling getVertexTaxResponse()...: %s",
						"CatTaxCustomApproverTM response file before parsing : -  "
								+ respFile);
				// Parsing XML and populating field in Ariba.....
				Log.customer.debug(" Parsing XML file ...........: %s",
						"CatTaxCustomApproverTM", "");
				File file1 = new File(respFile);
				DocumentBuilderFactory dbf = DocumentBuilderFactory
						.newInstance();
				DocumentBuilder db = dbf.newDocumentBuilder();
				Document doc = db.parse(file1);
				if (respFile != null) {

					doc.getDocumentElement().normalize();
					Log.customer.debug("Root element "
							+ doc.getDocumentElement().getNodeName());
					NodeList nodeList = doc.getElementsByTagName("LineItem");
					Log.customer.debug("Information of all Line Item nodeList "
							+ nodeList.getLength());

					for (int s = 0; s < nodeList.getLength(); s++) {
						// Log.customer.debug("s : in parse method : - " + s +
						// " nodeList " + nodeList.getLength());
						Node fstNode = nodeList.item(s);
						Log.customer.debug("*** lineItemNumber before : ");
						Element fstElmntlnm = (Element) fstNode;
						String lineItemNumber = fstElmntlnm
								.getAttribute("lineItemNumber");
						Log.customer
								.debug("*** lineItemNumber outside loop  after: "
										+ lineItemNumber);
						int index = Integer.parseInt(lineItemNumber);
						Log.customer
								.debug("*** lineItemNumber outside loop  after: "
										+ index);
						try {
							int plinumber = index - 1;
							Log.customer
									.debug("*** lineItemNumber plinumber  after: "
											+ plinumber);
							pli = (ProcureLineItem) lineItems.get(plinumber);

						} catch (Exception e) {
							Log.customer.debug("*** in catch of pli : "
									+ lineItemNumber + " ******** "
									+ e.toString());
							Log.customer.debug(pli.toString());
							Log.customer.debug(e.getClass());
						}
						// pli = (ProcureLineItem) lineItems.get(s);
						if (fstNode.getNodeType() == Node.ELEMENT_NODE) {

							Element fstElmnt = (Element) fstNode;
							NodeList countryElmntLst = fstElmnt
									.getElementsByTagName("Country");
							Element lstNmElmnt = (Element) countryElmntLst
									.item(0);
							NodeList lstNm = lstNmElmnt.getChildNodes();
							Log.customer.debug("*** Country : "
									+ ((Node) lstNm.item(0)).getNodeValue());

							// Total Tax
							NodeList totaltaxElmntLst = fstElmnt
									.getElementsByTagName("TotalTax");
							Element lstNmElmnt1 = (Element) totaltaxElmntLst
									.item(0);
							NodeList lstNm1 = lstNmElmnt1.getChildNodes();
							Log.customer
									.debug("*** TotalTax after population : "
											+ ((Node) lstNm1.item(0))
													.getNodeValue());
							String totalTax = ((Node) lstNm1.item(0))
									.getNodeValue();
							BigDecimal taxAmount = new BigDecimal(totalTax);
							Money taxTotal = new Money(taxAmount, pli
									.getAmount().getCurrency());
							pli.setFieldValue("TaxAmount", taxTotal);
							Log.customer.debug("*** Tax Amount : " + totalTax);

							// Reason code
							Element fstElmntRC = (Element) fstNode;
							NodeList lstNmElmntLstRC = fstElmntRC
									.getElementsByTagName("AssistedParameter");
							String ReasonCode = "";
							Log.customer
									.debug("*** lstNmElmntLstRC.getLength() : "
											+ lstNmElmntLstRC.getLength());
							for (int b = 0; b < lstNmElmntLstRC.getLength(); b++) {
								Node fstNodeRC = lstNmElmntLstRC.item(b);
								if (fstNodeRC.getNodeType() == Node.ELEMENT_NODE) {
									Element fstElmntRC1 = (Element) fstNodeRC;
									String fieldIdRC = fstElmntRC1
											.getAttribute("phase");
									Log.customer
											.debug("*** ReasonCode in loop : "
													+ fieldIdRC);
									if ("POST".equalsIgnoreCase(fieldIdRC)) {
										// Log.customer.debug("*** in IF  : "+
										// fieldIdRC);
										try {
											Element lstNmElmntRC = (Element) lstNmElmntLstRC
													.item(0);
											if (lstNmElmntRC.equals(null)
													|| lstNmElmntRC.equals("")) {
												ReasonCode = "";
												Log.customer
														.debug(
																"%s *** ReasonCode in if : %s",
																className,
																ReasonCode);
											} else {
												NodeList lstNmRC = lstNmElmntRC
														.getChildNodes();
												ReasonCode = ((Node) lstNmRC
														.item(0))
														.getNodeValue();
												Log.customer
														.debug(
																"%s *** ReasonCode in else : %s",
																className,
																ReasonCode);
											}

										} catch (NullPointerException e) {
											Log.customer
													.debug(
															"%s *** inside exception : %s",
															className);
										}
										/*****************************************/
									} else {
										Log.customer.debug("inside else");
										break;
									}
								}

								Log.customer.debug("inside loop still....");
							}
							Log.customer.debug("outside loop .....");
							// *********************************************************************************

							// TaxAmount = 0 and Reason code = Null then exempt
							// Reason code is E0.

							// Start :  RSD 111 (FRD4.0/TD 1.2)

							String sapsource = null;
							sapsource = (String)pli.getLineItemCollection().getDottedFieldValue("CompanyCode.SAPSource");

							Log.customer.debug("*** ReasonCode logic RSD111 SAPSource is: %s",sapsource);

							if((sapsource.equals("MACH1")) && ((ReasonCode != null) || (!""
									.equals(ReasonCode))))
							{
								/** Fetching Description from Table. */
								Log.customer.debug("*** ReasonCode logic RSD111: " + ReasonCode);
								String taxCodeForLookup = ReasonCode; // Please Replace with
																		// the Actual value
																		// from Web Service
																		// Response.
								String qryStringrc = "Select TaxExemptDescription from cat.core.TaxExemptReasonCode where TaxExemptUniqueName  = '"
										+ taxCodeForLookup + "'";
								Log.customer.debug("%s TaxExemptReasonCode : qryString "
										+ qryStringrc);
								// Replace the cntrctrequest - Invoice Reconciliation Object
								AQLOptions queryOptionsrc = new AQLOptions(ar
										.getPartition());
								AQLResultCollection queryResultsrc = Base.getService()
										.executeQuery(qryStringrc, queryOptionsrc);
								if (queryResultsrc != null) {
									Log.customer
											.debug(" RSD111 -- TaxExemptReasonCode: Query Results not null");
									while (queryResultsrc.next()) {
										String taxdescfromLookupvalue = (String) queryResultsrc
												.getString(0);
										Log.customer
												.debug(" RSD111 TaxExemptReasonCode: taxdescfromLookupvalue = "
														+ taxdescfromLookupvalue);
										// Change the rli to appropriate Carrier Holding
										// object, i.e. IR Line object
										if ("".equals(taxdescfromLookupvalue)
												|| taxdescfromLookupvalue == null
												|| "null".equals(taxdescfromLookupvalue)) {
											// if(taxdescfromLookupvalue.equals("")||taxdescfromLookupvalue
											// ==
											// null||taxdescfromLookupvalue.equals("null")
											// ){
											taxdescfromLookupvalue = "";
										}
										pli
												.setFieldValue("Carrier",
														taxdescfromLookupvalue);
										Log.customer
												.debug(" RSD111 -- TaxExemptReasonCode Applied on Carrier:  "
														+ taxdescfromLookupvalue);
									}
								}

								// End :  RSD 111 (FRD4.0/TD 1.2)


								} else if (("0.0".equals(totalTax) && ((ReasonCode == null) || (""
											.equals(ReasonCode))))) {
										ReasonCode = "E0";
										Log.customer.debug(
												"*** ReasonCode in condition : %s",
												className, ReasonCode);
									} else if (("0.0".equals(totalTax) && ((ReasonCode != null) || (!""
											.equals(ReasonCode))))) {

										/** Fetching Description from Table. */
										Log.customer.debug("*** ReasonCode after : "
												+ ReasonCode);
										String taxCodeForLookup = ReasonCode; // Please
															// Replace
															// with
															// the
															// Actual
															// value
															// from
															// Web
															// Service
															// Response.
								String qryStringrc = "Select TaxExemptDescription from cat.core.TaxExemptReasonCode where TaxExemptUniqueName  = '"
										+ taxCodeForLookup + "'";
								Log.customer
										.debug("%s TaxExemptReasonCode : qryString "
												+ qryStringrc);
								// Replace the cntrctrequest - Invoice
								// Reconciliation Object
								AQLOptions queryOptionsrc = new AQLOptions(ar
										.getPartition());
								AQLResultCollection queryResultsrc = Base
										.getService().executeQuery(qryStringrc,
												queryOptionsrc);
								if (queryResultsrc != null) {
									Log.customer
											.debug(" TaxExemptReasonCode: Query Results not null");
									while (queryResultsrc.next()) {
										String taxdescfromLookupvalue = (String) queryResultsrc
												.getString(0);
										Log.customer
												.debug(" TaxExemptReasonCode: taxdescfromLookupvalue ="
														+ taxdescfromLookupvalue);
										// Change the rli to appropriate Carrier
										// Holding object, i.e. IR Line object
										if ("".equals(taxdescfromLookupvalue)
												|| taxdescfromLookupvalue == null
												|| "null"
														.equals(taxdescfromLookupvalue)) {
											// if(taxdescfromLookupvalue.equals("")||taxdescfromLookupvalue
											// ==
											// null||taxdescfromLookupvalue.equals("null")
											// ){
											taxdescfromLookupvalue = "";
										}
										pli.setFieldValue("Carrier",
												taxdescfromLookupvalue);
										Log.customer
												.debug(" TaxExemptReasonCode Applied on Carrier:  "
														+ taxdescfromLookupvalue);
									}
								}
							}

							// *****************************************************************************//*
							// tax code logic ...
							if (totalTax.equals("0.0")) {
								String formattedString = companyCode + "_"
										+ state + "_" + "B0";
								Log.customer.debug("***formattedString : "
										+ formattedString);
								String qryString = "Select TaxCode,UniqueName, SAPTaxCode from ariba.tax.core.TaxCode where UniqueName  = '"
										+ formattedString
										+ "' and Country.UniqueName ='"
										+ pli
												.getDottedFieldValue("ShipTo.Country.UniqueName")
										+ "'";
								Log.customer
										.debug(" CatMSCTaxCodeDetermination : REQUISITION: qryString "
												+ qryString);
								AQLOptions queryOptions = new AQLOptions(ar
										.getPartition());
								Log.customer
										.debug(" CatMSCTaxCodeDetermination: REQUISITION - Stage I");
								AQLResultCollection queryResults = Base
										.getService().executeQuery(qryString,
												queryOptions);
								Log.customer
										.debug(" CatMSCTaxCodeDetermination: REQUISITION - Stage II- Query Executed");
								if (queryResults != null) {
									Log.customer
											.debug(" CatMSCTaxCodeDetermination: REQUISITION - Stage III - Query Results not null");
									while (queryResults.next()) {
										Log.customer
												.debug(" CatMSCTaxCodeDetermination: REQUISITION - Stage IV - Entering the DO of DO-WHILE");
										Log.customer
												.debug(" CatMSCTaxCodeDetermination: REQUISITION - Stage IV - Entering the DO of DO-WHILE"
														+ queryResults
																.getBaseId(0)
																.get());
										TaxCode taxfromLookupvalue = (TaxCode) queryResults
												.getBaseId(0).get();
										Log.customer
												.debug(" taxfromLookupvalue"
														+ taxfromLookupvalue);
										Log.customer
												.debug(" CatMSCTaxCodeDetermination : REQUISITION: TaxCodefromLookup"
														+ taxfromLookupvalue);
										// Set the Value of
										// LineItem.TaxCode.UniqueName =
										// 'formattedString'
										Log.customer
												.debug(" CatMSCTaxCodeDetermination : REQUISITION: Setting TaxCodefromLookup"
														+ taxfromLookupvalue);
										pli.setFieldValue("TaxCode",
												taxfromLookupvalue);
										Log.customer
												.debug(" CatMSCTaxCodeDetermination : REQUISITION: Applied "
														+ taxfromLookupvalue);
									}
								}
							}
							// end Tax code...

							Log.customer.debug("*** After loop Tax code  : ");
						}
					}
				}
			} catch (ParserConfigurationException e) {
				// TODO Auto-generated catch block
				Log.customer
						.debug("... Error In ParserConfigurationException ...");
				Log.customer.debug("... Error Message ..." + e.getMessage());
				e.printStackTrace();
			} catch (TransformerException e) {
				// TODO Auto-generated catch block
				Log.customer.debug("... Error In Transforming ...");
				Log.customer.debug("... Error Message ..." + e.getMessage());
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				Log.customer.debug("In IO Exception");
				Log.customer.debug("in Exception" + e.getMessage());
				e.printStackTrace();
			} catch (NullPointerException e) {
				Log.customer.debug("In Null Exception");
				Log.customer.debug("in Exception" + e.getMessage());
			} catch (SAXException e) {
				Log.customer.debug("In SAX Exception");
				Log.customer.debug("in Exception" + e.getMessage());
			} catch (Exception a) {
				Log.customer
						.debug("Inside Exception . Fix the request and resend.");
				Log.customer.debug("in Exception" + a.getCause());
				Log.customer.debug("in Exception" + a.getMessage());
			}
		}
	}

	/*
	 * Method Name : addIPToPR
	 * Input Parameters: ApprovalRequest ar,Approvable r
	 * Return Type: None
	 *
	 * This method adds the IP team when there is data issue or processing
	 * request file with wrong data.
	 */
	public static void addIPToPR(Approvable r, ApprovalRequest ar) {
		String role = "IP";
		Object obj = CatCommonUtil.getRoleforSplitterRuleForVertex(r, role);
		ProcureLineItemCollection plic = (ProcureLineItemCollection) r;
		String TaxReason = "Tax Reason";
		boolean flag1 = true;
		Log.customer.debug("%s ::: isIPRequired - plic bfore create %s",
				className, plic.toString());
		ApprovalRequest approvalrequest1 = ApprovalRequest.create(plic,
				((ariba.user.core.Approver) (obj)), flag1, "RuleReasons",
				TaxReason);
		Log.customer.debug("%s ::: approvalrequest1 got activated- %s",
				className);

		BaseVector basevector1 = plic.getApprovalRequests();
		Log.customer.debug("%s ::: basevector1 got activated- %s", className);

		BaseVector basevector2 = approvalrequest1.getDependencies();
		Log.customer.debug("%s ::: basevector2 got activated- %s", className);

		basevector2.add(0, ar);
		Log.customer.debug("%s ::: ar added to basevector2 %s", className);

		approvalrequest1.setFieldValue("Dependencies", basevector2);
		ar.setState(2);
		Log.customer.debug("%s ::: ar.setState- %s", className);

		ar.updateLastModified();
		Log.customer.debug("%s ::: ar.updatelastmodified- %s", className);

		basevector1.removeAll(ar);
		Log.customer.debug("%s ::: basevecotr1 .removeall %s", className);

		basevector1.add(0, approvalrequest1);
		Log.customer.debug("%s ::: basevector1 .add- %s", className);

		plic.setApprovalRequests(basevector1);
		Log.customer.debug("%s ::: ir .setApprovalRequests got activated- %s",
				className);

		java.util.List list = ListUtil.list();
		java.util.Map map = MapUtil.map();
		boolean flag6 = approvalrequest1.activate(list, map);

		Log.customer.debug("%s ::: New TaxAR Activated - %s", className);
		Log.customer.debug("%s ::: State (AFTER): %s", className);
		Log.customer.debug("%s ::: Approved By: %s", className);


	}
	/*
	 * Method Name : addMSCAdminToIR
	 * Input Parameters: ApprovalRequest ar,Approvable lic
	 * Return Type: None
	 *
	 * This method adds the MSC Admin when there is vertex Login issue(Purely
	 * for login issues-incorrect user id/pwd
	 */
	public static void addMSCAdminToPR(ApprovalRequest ar, Approvable lic) {
		Log.customer.debug("%s ::: addMSCADMinToPR - %s", className, lic);
		ProcureLineItemCollection plic = (ProcureLineItemCollection) lic;
		String TaxRole = "MSC Administrator";
		String TaxReason = "Tax Reason";
		boolean flag1 = true;
		Object obj = Role.getRole(TaxRole);
		// plic.setFieldValue("ProjectID","F");
		Log.customer.debug("%s ::: isMSCAdminRequired - plic bfore create %s",
				className, plic.toString());
		ApprovalRequest approvalrequest1 = ApprovalRequest.create(plic,
				((ariba.user.core.Approver) (obj)), flag1, "RuleReasons",
				TaxReason);
		Log.customer.debug("%s ::: approvalrequest1 got activated- %s",
				className);

		BaseVector basevector1 = plic.getApprovalRequests();
		Log.customer.debug("%s ::: basevector1 got activated- %s", className);

		BaseVector basevector2 = approvalrequest1.getDependencies();
		Log.customer.debug("%s ::: basevector2 got activated- %s", className);

		basevector2.add(0, ar);
		Log.customer.debug("%s ::: ar added to basevector2 %s", className);

		approvalrequest1.setFieldValue("Dependencies", basevector2);
		ar.setState(2);
		Log.customer.debug("%s ::: ar.setState- %s", className);

		ar.updateLastModified();
		Log.customer.debug("%s ::: ar.updatelastmodified- %s", className);

		basevector1.removeAll(ar);
		Log.customer.debug("%s ::: basevecotr1 .removeall %s", className);

		basevector1.add(0, approvalrequest1);
		Log.customer.debug("%s ::: basevector1 .add- %s", className);

		plic.setApprovalRequests(basevector1);
		Log.customer.debug("%s ::: ir .setApprovalRequests got activated- %s",
				className);

		java.util.List list = ListUtil.list();
		java.util.Map map = MapUtil.map();
		boolean flag6 = approvalrequest1.activate(list, map);

		Log.customer.debug("%s ::: New TaxAR Activated - %s", className);
		Log.customer.debug("%s ::: State (AFTER): %s", className);
		Log.customer.debug("%s ::: Approved By: %s", className);

	}
	/*
	 * Method Name : addVertexManagerToPR
	 * Input Parameters: ApprovalRequest ar,Approvable lic
	 * Return Type: None
	 *
	 * This method adds the Vertex Manger when there is webservice (Purely for
	 * webservice is down or not reachable).
	 */
	public static void addVertexManagerToPR(ApprovalRequest ar, Approvable lic) {
		Log.customer.debug("%s ::: addVertexManagerToPR - %s", className, lic);
		ProcureLineItemCollection plic = (ProcureLineItemCollection) lic;
		String TaxRole = "VertexManager";
		String TaxReason = "Tax Reason";
		boolean flag1 = true;
		Object obj = Role.getRole(TaxRole);
		// plic.setFieldValue("ProjectID","F");
		Log.customer.debug(
				"%s ::: isTaxManagerRequired - plic bfore create %s",
				className, plic.toString());
		ApprovalRequest approvalrequest1 = ApprovalRequest.create(plic,
				((ariba.user.core.Approver) (obj)), flag1, "RuleReasons",
				TaxReason);
		Log.customer.debug("%s ::: approvalrequest1 got activated- %s",
				className);

		BaseVector basevector1 = plic.getApprovalRequests();
		Log.customer.debug("%s ::: basevector1 got activated- %s", className);

		BaseVector basevector2 = approvalrequest1.getDependencies();
		Log.customer.debug("%s ::: basevector2 got activated- %s", className);

		basevector2.add(0, ar);
		Log.customer.debug("%s ::: ar added to basevector2 %s", className);

		approvalrequest1.setFieldValue("Dependencies", basevector2);
		ar.setState(2);
		Log.customer.debug("%s ::: ar.setState- %s", className);

		ar.updateLastModified();
		Log.customer.debug("%s ::: ar.updatelastmodified- %s", className);

		basevector1.removeAll(ar);
		Log.customer.debug("%s ::: basevecotr1 .removeall %s", className);

		basevector1.add(0, approvalrequest1);
		Log.customer.debug("%s ::: basevector1 .add- %s", className);

		plic.setApprovalRequests(basevector1);
		Log.customer.debug("%s ::: ir .setApprovalRequests got activated- %s",
				className);

		java.util.List list = ListUtil.list();
		java.util.Map map = MapUtil.map();
		boolean flag6 = approvalrequest1.activate(list, map);

		Log.customer.debug("%s ::: New TaxAR Activated - %s", className);
		Log.customer.debug("%s ::: State (AFTER): %s", className);
		Log.customer.debug("%s ::: Approved By: %s", className);

	}
	/*
	 * Method Name : addTaxManagerToPR
	 * Input Parameters: ApprovalRequest ar,Approvable lic
	 * Return Type: None
	 *
	 * This method adds the Tax Manger when there is Data issue (Purely with
	 * generating request file and processing requestfile with wrong data).
	 */
	public static void addTaxManagerToPR(ApprovalRequest ar, Approvable lic) {
		Object[] isTaxManagerRequired = new Object[1];
		isTaxManagerRequired[0] = "true";
		ProcureLineItemCollection plic = (ProcureLineItemCollection) lic;
		if (isTaxManagerRequired != null) {
			String approvalRequiredFlag = isTaxManagerRequired[0].toString();
			// String approvalReason = isTaxManagerRequired[1].toString();
			if (approvalRequiredFlag.equals("true")) {
				User user = User.getAribaSystemUser(ar.getPartition());
				String TaxRole = "Tax Manager";
				String TaxReason = "Tax Reason";
				boolean flag1 = true;
				Object obj = Role.getRole(TaxRole);
				plic.setFieldValue("TaxOverrideFlag", "true");
				ApprovalRequest approvalrequest1 = ApprovalRequest.create(plic,
						((ariba.user.core.Approver) (obj)), flag1,
						"RuleReasons", TaxReason);
				Log.customer.debug("%s ::: approvalrequest1 got activated- ");

				BaseVector basevector1 = plic.getApprovalRequests();
				Log.customer.debug("%s ::: basevector1 got activated- ");

				BaseVector basevector2 = approvalrequest1.getDependencies();
				Log.customer.debug("%s ::: basevector2 got activated- ");

				basevector2.add(0, ar);
				Log.customer.debug("%s ::: ar added to basevector2 ");

				approvalrequest1.setFieldValue("Dependencies", basevector2);
				ar.setState(2);
				Log.customer.debug("%s ::: ar.setState- ");

				ar.updateLastModified();
				Log.customer.debug("%s ::: ar.updatelastmodified- ");

				basevector1.removeAll(ar);
				Log.customer.debug("%s ::: basevecotr1 .removeall ");

				basevector1.add(0, approvalrequest1);
				Log.customer.debug("%s ::: basevector1 .add- ");

				plic.setApprovalRequests(basevector1);
				Log.customer
						.debug("%s ::: ir .setApprovalRequests got activated- ");

				java.util.List list = ListUtil.list();
				java.util.Map map = MapUtil.map();
				boolean flag6 = approvalrequest1.activate(list, map);

				Log.customer.debug("%s ::: New TaxAR Activated - ");
				Log.customer.debug("%s ::: State (AFTER): ");
				Log.customer.debug("%s ::: Approved By: %s");
			} else
				plic.setFieldValue("TaxOverrideFlag", "false");

		}
	}

	public CatSAPTaxCustomApproverTM() {
	};
}