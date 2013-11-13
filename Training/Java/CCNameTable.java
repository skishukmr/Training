/*

 Date                       Name of the Programmer	Ref to the Frd section/technical doc.		 Description of the code change
 8/5/2013                    Rupesh Bhayana          RSD # 116									 nametable to have commodity code visible based on visibleOnUI field

*/

package config.java.nametable;

import ariba.base.core.aql.AQLCondition;
import ariba.base.core.aql.AQLQuery;
import ariba.base.core.aql.SearchTermQuery;
import ariba.base.core.aql.AQLNameTable;
import ariba.basic.core.CommodityCode;
import ariba.peoplesoft.common.CommodityCodeNameTable;
import ariba.util.log.Log;


public class CCNameTable extends AQLNameTable
{
    public void addQueryConstraints (AQLQuery query, String field,
                                     String pattern, SearchTermQuery searchTermQuery)
    {
	super.addQueryConstraints(query,field,pattern,searchTermQuery);
    	Log.customer.debug("add for CC 1---" +  query);

        String Cc = "ariba.basic.core.CommodityCode";

        AQLCondition supplierTest = AQLCondition.buildEqual(query.buildField(
            "visibleOnUI"), Boolean.TRUE);

        query.and(supplierTest);

        Log.customer.debug("add for CC 2---" +  query);
    }
}
