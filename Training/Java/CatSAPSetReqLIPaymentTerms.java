/*
 * 1.	IBM Nandini Bheemaiah	25/09/2013  Q4 2013-RSD114-FDD5.0/TDD1.0    To Update the Payment Term for Copied PRs and Copied Line Items.

 */

package config.java.action.sap;



import ariba.approvable.core.LineItemCollection;
import ariba.base.core.Base;
import ariba.base.core.BaseId;
import ariba.base.core.BaseObject;
import ariba.base.core.ClusterRoot;
import ariba.base.core.aql.AQLOptions;
import ariba.base.core.aql.AQLQuery;
import ariba.base.core.aql.AQLResultCollection;
import ariba.base.fields.Action;
import ariba.base.fields.ValueSource;
import ariba.common.core.SupplierLocation;
import ariba.payment.core.PaymentTerms;
import ariba.purchasing.core.ReqLineItem;
import ariba.util.core.PropertyTable;
import ariba.util.core.ResourceService;
import ariba.util.log.Log;
public class CatSAPSetReqLIPaymentTerms extends Action {

	private static final String classname = "CatSAPSetReqLIPaymentTerms : ";


	private static final String StringTable = "aml.DefaultPartitionedPaymentTerms"; // Resource File Name


	public void fire(ValueSource object, PropertyTable params){

		Log.customer.debug("%s ***fire()",classname);

		LineItemCollection lic = null;
		BaseObject companyCode = null;
		ClusterRoot paymentTermObject = null;
		PaymentTerms payTerm = null;
		SupplierLocation suploc = null;

		String reqSapSource = null;
		String queryString = "";
		String key = null;

		try
		{
			if (object instanceof ReqLineItem) {
				Log.customer.debug("%s ***Getting into as its a Req Line Item PR",classname);


				ReqLineItem rli = (ReqLineItem)object;


				/*
				 *  if the PR is copied PR, go to line items and check if there is value in the
				 *  line items payment term is blank,if yes only then ,proceed to update the payment term
				 */


				payTerm = (PaymentTerms) rli.getFieldValue("PaymentTerms");
				if(payTerm == null)
				{
					Log.customer.debug("%s ***Payment Term is null. Set Payment Term on PR",classname);

					// To fetch the Company Code
					lic =  (LineItemCollection)rli.getLineItemCollection();
					if(lic!=null){
						Log.customer.debug("%s ***Step 2 : Get CompanyCode details",classname);
						companyCode = (BaseObject)lic.getDottedFieldValue("CompanyCode");
						Log.customer.debug("CompanyCode Object is :" +companyCode);
						Log.customer.debug("CompanyCode UniqueName:" +lic.getDottedFieldValue("CompanyCode.UniqueName"));

					}
					// To fetch the supplier location
					if (rli.getSupplierLocation() != null)
					{
						Log.customer.debug("%s ***Step 3 : Get SupplierLocation Details",classname);
						Log.customer.debug("Sup Loc Name of copied PR "+rli.getSupplierLocation().getName());

						suploc = rli.getSupplierLocation();

						Log.customer.debug("%s ***Functionality Begins",classname);
						Log.customer.debug("%s ***Step 4 : Get PaymentTerms from Supplier Location",classname);
						if(suploc.getPaymentTerms() != null)
						{
							payTerm = (PaymentTerms)suploc.getPaymentTerms();
							Log.customer.debug("%s ***Step 5 : Setting Payment Terms from Supplier Location",classname);
							Log.customer.debug("SupplierLoc PaymentTerms:"+ suploc.getPaymentTerms().getUniqueName());
						}
						else if(companyCode != null){
							Log.customer.debug("%s *** Supplier Location Payment Term is null, Go to Company Code ",classname);
							Log.customer.debug("%s ***Step 6 : Setting Payment Terms from Company Code",classname);
							payTerm = (PaymentTerms)companyCode.getFieldValue("DefaultPaymentTerms");
							Log.customer.debug("CompanyCode Default PaymentTerms:" +payTerm);
							/*
							 * If the payment Term in Supplier location and CompanyCode is null,
							 * take the payment term value from the resource file and update the copied PR.
							 */
							if(payTerm == null)
							{

								Log.customer.debug("%s ***Step 7 :CompanyCode PaymentTerms is null get the default Payment terms for SAP Source:" ,classname);
								reqSapSource = (String)companyCode.getFieldValue("SAPSource");
								Log.customer.debug("SAP Source from CompanyCode:" +reqSapSource);
								key = reqSapSource;

								Log.customer.debug("%s ***Step 8:Get KeyValue from Resource file" ,classname);
								String keyValue = ResourceService.getString(StringTable,key);
								Log.customer.debug("KeyValue from Resource file:"+keyValue);

								Log.customer.debug("%s ***Step 9 :Retrieving Payment Term based on the keyValue",classname);
								queryString ="Select this from ariba.payment.core.PaymentTerms where UniqueName = '" + keyValue + "'";
								Log.customer.debug("Query is : " + queryString);
								AQLQuery aqlQuery = AQLQuery.parseQuery(queryString);
								AQLResultCollection results = Base.getService().executeQuery(aqlQuery, baseOptions());
								if (results.getErrors() != null)
								{
									Log.customer.debug(results.getFirstError().toString());
									Log.customer.debug("No PaymentTerms for the SAP Source" + companyCode );
								}
								else
								{
									try
									{
										if (results.next())
										{
											BaseId bid = results.getBaseId(0);
											paymentTermObject = 	Base.getSession().objectFromId(bid);
											if(paymentTermObject != null)
											{
												Log.customer.debug("Payment Term object is not null");
												payTerm = (PaymentTerms) paymentTermObject;
												Log.customer.debug("Setting Default Payment term by SAPSource"+payTerm.getUniqueName());
											}

											Log.customer.debug("Payment Term from Resource file to DB  = " + paymentTermObject.getFieldValue("UniqueName"));
										}
										else
										{
											Log.customer.debug("No Results found");
										}

									}
									catch(Exception e)
									{
										Log.customer.debug(" Exception while accessing Payment Terms" );
									}
								}



							}else
							{
								Log.customer.debug("No Results found");
							}

						}

					}else
					{
						Log.customer.debug("Supplier Location is null: Payment Term cannot be set");
					}/*End if-else when supploc is null*/


					rli.setFieldValue("PaymentTerms", payTerm);
					key = null;
				}else
				{
					Log.customer.debug("Payment Term is already Set");
				}
			}
	}catch (Exception e)
		{
			Log.customer.debug("Exception : Caught ");

		}

	}
	

	public AQLOptions baseOptions() {
		Log.customer.debug("Going into baseOption method");

		AQLOptions options = new AQLOptions();
		options.setRowLimit(0);
		options.setUserLocale(Base.getSession().getLocale());
		options.setUserPartition(Base.getSession().getPartition());
		Log.customer.debug("Going into baseOption method : END");

		return options;
	}
	
}