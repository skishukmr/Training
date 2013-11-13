/*
 * 1.		IBM Nandini Bheemaiah	28/08/2013 Q4 2013-RSD119-FDD5.0/TDD1.0    To check if the Requistion has OIOSupplier in the line item.

 */
package config.java.condition.sap;

import ariba.base.core.BaseVector;
import ariba.base.fields.Condition;
import ariba.base.fields.ConditionEvaluationException;
import ariba.common.core.SupplierLocation;
import ariba.procure.core.ProcureLineItem;
import ariba.procure.core.ProcureLineItemCollection;
import ariba.util.core.PropertyTable;
import ariba.util.core.ResourceService;
import ariba.util.log.Log;

public class CatReqContainsOIOSupplier extends Condition {

	private static final String THISCLASS = "CatReqContainsOIOSupplier";
	private static String OIOSuplr = ResourceService.getString("cat.java.sap","Req_OIOSuplrUniqueName");


    
	
	public boolean evaluate(Object object, PropertyTable params) throws ConditionEvaluationException {
    	boolean hasOIO = false;

        try {
			
			String supplierName = null;
			if (object instanceof ProcureLineItemCollection) {
			    ProcureLineItemCollection r = (ProcureLineItemCollection)object;

			    
			    if (r.getLineItemsCount() > 0) {
			        BaseVector lines = r.getLineItems();
			        int size = lines.size();
			        Log.customer.debug("%s *** Total number of line items: %s", THISCLASS,size);
			        for (;size>0;size--) {
			            ProcureLineItem pli = (ProcureLineItem)lines.get(size-1);
			            SupplierLocation sloc = pli.getSupplierLocation();
			            supplierName = sloc.getSupplier().getUniqueName();
			            if (sloc != null && OIOSuplr.equalsIgnoreCase(supplierName)) {
			                Log.customer.debug("%s *** FOUND OIO Supplier Location!", THISCLASS);
			                hasOIO = true;
			                break;
			            }
			        }
			    }
			}
			Log.customer.debug("CatReqContainsOIOSupplier *** hasOIOSupplier? " + hasOIO);
			
		} catch (Exception e)
		{
			Log.customer.debug("Exception Occured : " + e);

			Log.customer.debug("Exception Details :" + ariba.util.core.SystemUtil.stackTrace(e));
		} 

		
			return hasOIO;
		
    }



	public CatReqContainsOIOSupplier() {
		super();
	}

}
