/*****************************************************************************************
* Change History
*****************************************************************************************
* Change By				Change Date			Description
*****************************************************************************************
1.  Shailaja Salimath 	11/11/09   			Issue 1013  New order chooser name table.. synchronised with other partitions
2.	Arasan Rajendren	03/11/11			Filter Only Orders in "Open, ClosedForChange, ClosedForReceiving" State.
3.  IBM Nandini 		10/09/13            Q4 2013-RSD119-FDD5.0/TDD1.0  Code Changes done to not fetch OIO enabled POs
											while creating invoice eforms for SAP and LSAP partition
*****************************************************************************************
*/

package config.java.invoiceeform.vcsv1;

import java.util.List;
import ariba.base.core.Base;
import ariba.base.core.Partition;
import ariba.base.core.aql.AQLNameTable;
import ariba.base.core.aql.AQLOptions;
import ariba.base.core.aql.AQLQuery;
import ariba.base.core.aql.AQLResultCollection;
import ariba.base.core.aql.SearchTermQuery;
import ariba.invoicing.core.Log;

public class CatCSVPONameTable extends AQLNameTable {

	AQLQuery query;
	AQLOptions queryOptions;
	AQLResultCollection queryResults;

	public CatCSVPONameTable() {
		super();
	}

	public List matchPattern(String field, String pattern, SearchTermQuery searchTermQuery) {

		String qryString_US = "SELECT PurchaseOrder, PurchaseOrder.OrderID, PurchaseOrder.TotalCost.Amount FROM "
				+ "ariba.purchasing.core.PurchaseOrder AS PurchaseOrder WHERE PurchaseOrder.Creator IS NULL AND "
				+ "(PurchaseOrder.OIOAgreement IS NULL OR PurchaseOrder.OIOAgreement = FALSE) AND "
				+ "PurchaseOrder.Closed IN (1,2,3)";
		String qryString_NONUS = "SELECT PurchaseOrder, PurchaseOrder.OrderID,PurchaseOrder.TotalCost.Amount FROM "
				+ "ariba.purchasing.core.PurchaseOrder AS PurchaseOrder WHERE PurchaseOrder.Creator IS NULL AND "
				+ "PurchaseOrder.Closed IN (1,2,3)";

		Log.customer.debug(" CatCSVPONameTable: pattern" + pattern);
		Log.customer.debug(" CatCSVPONameTable: field" + field);

		String partition = Base.getSession().getPartition().getName();

		//Start Q4 2013-RSD119-FDD5.0/TDD1.0 : Added OR condition to include SAP and LSAP partition check to avoid OIO enabled
		//PO to appear in the search during creation of Invoice eForm.
		if ((partition == "pcsv1")||(partition.equalsIgnoreCase("SAP"))||(partition.equalsIgnoreCase("LSAP"))) {
		//End Q4 2013-RSD119-FDD5.0/TDD1.0
			Log.customer.debug("CatCSVPONameTable for order: partition: %s", partition);
			if (pattern != null && (!pattern.equals("*"))) {
				String pattern1 = pattern.substring(1, pattern.length() - 1);
				if (field.equals("TotalCost.Amount")) {
					qryString_US = qryString_US + " AND " + field + " = '" + pattern1 + "'";
					Log.customer.debug( "final query 1 : CatCSVPONameTable for amount: %s", qryString_US);
				} else {
					qryString_US = qryString_US + " AND " + field + " =  '" + pattern1 + "'";
					Log.customer.debug("final query 2 : CatCSVPONameTable for order : %s", qryString_US);
				}
			}

			Partition currentPartition = Base.getSession().getPartition();
			AQLQuery query1 = AQLQuery.parseQuery(qryString_US);
			AQLOptions options = new AQLOptions(currentPartition);
			options.setRowLimit(140);
			AQLResultCollection results = Base.getService().executeQuery(query1, options);
			Log.customer.debug("CatCSVPONameTable Results Statement= %s", results);
			return results.getRawResults();

		} else {
			Log.customer.debug("CatCSVPONameTable for order :partition  %s", partition);
			if (pattern != null && (!pattern.equals("*"))) {
				String pattern1 = pattern.substring(1, pattern.length() - 1);
				if (field.equals("TotalCost.Amount")) {
					qryString_NONUS = qryString_NONUS + " AND " + field + " = '" + pattern1 + "'";
					Log.customer.debug( "final query 3 : CatCSVPONameTable for amount: %s", qryString_NONUS);
				} else
					qryString_NONUS = qryString_NONUS + " AND " + field + " = '" + pattern1 + "'";
				Log.customer.debug("final query 4 : CatCSVPONameTable for order : %s", qryString_NONUS);
			}
			Partition currentPartition = Base.getSession().getPartition();
			AQLQuery query1 = AQLQuery.parseQuery(qryString_NONUS);
			AQLOptions options = new AQLOptions(currentPartition);
			options.setRowLimit(140);
			AQLResultCollection results = Base.getService().executeQuery(query1, options);
			Log.customer.debug("CatCSVPONameTable Results Statement= %s", results);
			return results.getRawResults();
		}
	}
}
