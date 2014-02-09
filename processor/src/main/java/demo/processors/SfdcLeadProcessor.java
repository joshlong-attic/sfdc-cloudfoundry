package demo.processors;

import com.force.api.ForceApi;
import com.force.api.QueryResult;
import demo.SfdcBatchTemplate;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;


@Component
class SfdcLeadProcessor extends AbstractSfdcBatchProcessor {

    private ForceApi forceApi;
    private SfdcRestUtils sfdcRestUtils;
    private JdbcTemplate jdbcTemplate;

    @Autowired
    SfdcLeadProcessor(SfdcRestUtils sfdcRestUtils, SfdcBatchTemplate sfdcBatchTemplate, JdbcTemplate jdbcTemplate, ForceApi forceApi) {
        super(sfdcBatchTemplate);
        this.sfdcRestUtils = sfdcRestUtils;
        this.jdbcTemplate = jdbcTemplate;
        this.forceApi = forceApi;
    }

    private String camelCaseToTableCol(String x) {
        String col = "";
        int index = 0;
        for (char c : x.toCharArray()) {
            if (Character.isUpperCase(c)) {
                col = col + (index == 0 ? "" : "_") + c;
            } else {
                col = col + c;
            }
            index = index + 1;
        }
        return col.toLowerCase();
    }

    @Override
    public void doProcessMessage(String batchId, Message msg) {

        String[] sooqlColNames = (" AnnualRevenue,City,Company,ConvertedAccountId,ConvertedContactId,ConvertedDate,ConvertedOpportunityId,Country,CreatedById,CreatedDate, Description,Email,EmailBouncedDate,EmailBouncedReason,Fax," +
                "FirstName,Id,Industry,IsConverted,IsDeleted,IsUnreadByOwner,Jigsaw,JigsawContactId,LastActivityDate,LastModifiedById,LastModifiedDate,LastName,   LeadSource, " +
                "MasterRecordId,MobilePhone,Name,NumberOfEmployees, OwnerId,Phone,PostalCode,  Rating,Salutation, State,Status,Street,SystemModstamp,Title,Website  ").trim().split(",");

        String[] tableColNames = new String[sooqlColNames.length];

        for (int i = 0; i < sooqlColNames.length; i++) {
            sooqlColNames[i] = sooqlColNames[i].trim();
            tableColNames[i] = camelCaseToTableCol(sooqlColNames[i]);
        }

        String qMarks = StringUtils.repeat("?,", tableColNames.length);
        qMarks = qMarks.substring(0, qMarks.length() - 1);

        String toExecute = String.format(" insert into sfdc_lead( %s ) values ( %s )", StringUtils.join(tableColNames, ","), qMarks);

        QueryResult<Map> res = this.forceApi.query(String.format("SELECT %s  FROM Lead", StringUtils.join(sooqlColNames, ",")));

        for (Map<String, Object> row : res.getRecords()) {

            this.jdbcTemplate.update(toExecute,
                    row.get("AnnualRevenue"),
                    row.get("City"),
                    row.get("Company"),
                    row.get("ConvertedAccountId"),
                    row.get("ConvertedContactId"),
                    sfdcRestUtils.parseDate(row.get("ConvertedDate")),
                    row.get("ConvertedOpportunityId"),
                    row.get("Country"),
                    row.get("CreatedById"),
                    sfdcRestUtils.parseDate(row.get("CreatedDate")),
                    row.get("Description"),
                    row.get("Email"),
                    sfdcRestUtils.parseDate(row.get("EmailBouncedDate")),
                    row.get("EmailBouncedReason"),
                    row.get("Fax"),
                    row.get("FirstName"),
                    row.get("Id"),
                    row.get("Industry"),
                    row.get("IsConverted"),
                    row.get("IsDeleted"),
                    row.get("IsUnreadByOwner"),
                    row.get("Jigsaw"),
                    row.get("JigsawContactId"),
                    sfdcRestUtils.parseDate(row.get("LastActivityDate")),
                    sfdcRestUtils.parseDate(row.get("LastModifiedById")),
                    sfdcRestUtils.parseDate(row.get("LastModifiedDate")),
                    row.get("LastName"),
                    row.get("LeadSource"),
                    row.get("MasterRecordId"),
                    row.get("MobilePhone"),
                    row.get("Name"),
                    row.get("NumberOfEmployees"),
                    row.get("OwnerId"),
                    row.get("Phone"),
                    row.get("PostalCode"),
                    row.get("Rating"),
                    row.get("Salutation"),
                    row.get("State"),
                    row.get("Status"),
                    row.get("Street"),
                    row.get("SystemModstamp"),
                    row.get("Title"),
                    row.get("Website")
            );
        }

    }
}
