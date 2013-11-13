/*******************************************************************************************************************************************

	Creator: Vikram J Singh
	Description: Q4 2013 - RSD111 - FDD 1/TDD 1 - Find the paid invoices with null DWInvoiceFlag and set them to be ready to be pushed to DW
	ChangeLog: 9/4/2013
	
	Date		Name		History
	--------------------------------------------------------------------------------------------------------------

*******************************************************************************************************************************************/

package config.java.schedule.sap;

import java.util.List;
import ariba.util.core.Date;
import ariba.util.core.StringUtil;
import ariba.base.core.BaseObject;
import ariba.base.core.BaseVector;
import ariba.invoicing.core.InvoiceReconciliation;
import ariba.util.log.Log;
import ariba.base.core.*;
import ariba.base.core.aql.*;
import java.sql.*;
import ariba.util.formatter.DateFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import ariba.base.core.Partition;
import ariba.base.core.Base;
import ariba.util.scheduler.ScheduledTask;
import ariba.util.scheduler.ScheduledTaskException;
import ariba.util.scheduler.Scheduler;


public class SettingDWFlag extends ScheduledTask {


	private static String query;

	private static AQLQuery qry;

	private static AQLOptions options;

	private static AQLResultCollection results;

	private static ariba.invoicing.core.InvoiceReconciliation invrecon;


	public void run()

	{

				Partition partition = Base.getSession().getPartition();


		Log.customer.debug("partition "
				+ partition);

		try

		{

			query = new String(
					"select from ariba.invoicing.core.InvoiceReconciliation where  StatusString = 'Paid' and DWInvoiceFlag IS NULL");

			qry = AQLQuery.parseQuery(query);

			options = new AQLOptions(partition);

			results = Base.getService().executeQuery(qry, options);

			if (results.getErrors() != null)

			{
				Log.customer.debug("SettingDWFlag: ERROR IN GETTING RESULTS");
			}

			while (results.next())

			{
				invrecon = (ariba.invoicing.core.InvoiceReconciliation) (results
						.getBaseId("InvoiceReconciliation").get());

				Log.customer
						.debug("SettingDWFlag: IR Number"
								+ invrecon);

				if (invrecon == null)
					continue;


				invrecon.setFieldValue("DWInvoiceFlag", "InProcess");

				invrecon.setFieldValue("TopicName", "DWInvoicePush");

                                Base.getSession().transactionCommit(); 



			} // end of while


		} // end of try

		catch (Exception e)

		{

			return;

		}

	}
    public SettingDWFlag() {
    }
}
