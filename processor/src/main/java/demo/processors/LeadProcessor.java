package demo.processors;

import com.force.api.ForceApi;
import com.force.api.QueryResult;
import demo.BatchTemplate;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreatorFactory;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.util.*;


@Component
public class LeadProcessor extends AbstractBatchProcessor {

    private Logger logger = Logger.getLogger(getClass());
    private ForceApi forceApi;
    private RestUtils sfdcRestUtils;
    private JdbcTemplate jdbcTemplate;

    @Autowired
    public LeadProcessor(RestUtils sfdcRestUtils, BatchTemplate batchTemplate, JdbcTemplate jdbcTemplate, ForceApi forceApi) {
        super(batchTemplate);
        this.sfdcRestUtils = sfdcRestUtils;
        this.jdbcTemplate = jdbcTemplate;
        this.forceApi = forceApi;
    }

    private void log(String msg, Object... a) {
        logger.info(String.format(msg, a));
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

    private int type(Object o) {
        if (o instanceof String) return Types.VARCHAR;
        if (o instanceof Number) return Types.BIGINT;
        if (o instanceof Date) return Types.DATE;
        return Types.OTHER;
    }

    @Transactional
    @Override
    public void doProcessMessage(String batchId, Message msg) {
        String q = new String(msg.getBody());

        if (!org.springframework.util.StringUtils.hasText(q)) {
            return;
        }

        String[] sooqlColNames = ("  AnnualRevenue,City,Company,ConvertedAccountId,ConvertedContactId,ConvertedDate,ConvertedOpportunityId,Country,CreatedById,CreatedDate, Description,Email,EmailBouncedDate,EmailBouncedReason, " +
                "FirstName,Id,Industry,IsConverted,IsDeleted,IsUnreadByOwner,Jigsaw,JigsawContactId,LastActivityDate,LastModifiedById,LastModifiedDate,LastName,   LeadSource, " +
                "MasterRecordId,   OwnerId,Phone,PostalCode,  Rating,Salutation, State,Status,Street,SystemModstamp,Title,Website  ")
                .trim()
                .split(",");

        String[] tableColNames = new String[sooqlColNames.length];

        for (int i = 0; i < sooqlColNames.length; i++) {
            sooqlColNames[i] = sooqlColNames[i].trim();
            tableColNames[i] = camelCaseToTableCol(sooqlColNames[i]);
        }

        String qMarks = StringUtils.repeat("?,", 1 + tableColNames.length); //1 more because we have a batch_id column
        qMarks = qMarks.substring(0, qMarks.length() - 1);

        String query =
                String.format("SELECT %s FROM Lead ", StringUtils.join(sooqlColNames, ",")) +
                        " WHERE (Company LIKE '%" + q + "%' or Email LIKE '%@%" + q +
                        "%') and (  City <> '') and (City <>',') and (State <> '') and (Country <> '') ";

        QueryResult<Map> res = this.forceApi.query(query);

        String insertIntoLeadTableSql = String.format(" insert ignore into sfdc_lead(  sfdc_id, %s ) " +
                " values ( %s )", StringUtils.join(tableColNames, ","), qMarks);

        Set<String> sfdcIdsToAssignToBatch = new HashSet<>();

        Set<Number> numbers = new HashSet<>();


        for (Map<String, Object> row : res.getRecords()) {


            PreparedStatementCreatorFactory pscf = new PreparedStatementCreatorFactory(insertIntoLeadTableSql);
            pscf.setReturnGeneratedKeys(true);
            String sfdcId = (String) row.get("Id");
            Object[] parameters = new Object[]{
                    //  batchId,
                    sfdcId,
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
            };

            for (Object p : parameters) {
                pscf.addParameter(new SqlParameter(type(p)));
            }


            KeyHolder keyHolder = new GeneratedKeyHolder();
            int updatedRows = this.jdbcTemplate.update(pscf.newPreparedStatementCreator(parameters), keyHolder);

            Number generatedKey = keyHolder.getKey();
            if (updatedRows == 0 && generatedKey == null) {
                // then it already existed in the DB (insert ignore)
                sfdcIdsToAssignToBatch.add(sfdcId);
            } else {
                // otherwise accumulate it
                numbers.add(generatedKey);
            }
        }

        // account for the batch data
        List<Object[]> objects = new ArrayList<>();
        for (Number k : numbers) {
            objects.add(new Object[]{batchId, k});
        }

        log("there are " + numbers.size() + " values accumulated for the batch table");

        int[] updatedRows = jdbcTemplate.batchUpdate("insert   into sfdc_batch_lead( batch_id, lead_id) values(?,?)", objects);

        List<String> inClause = new ArrayList<>();
        for (String x : sfdcIdsToAssignToBatch) {
            inClause.add(String.format("'%s'", x));
        }
        String insertTheRest = String.format(
                "insert  into sfdc_batch_lead ( batch_id, lead_id) select ?, " +
                        " sl._id from sfdc_lead sl where sl.sfdc_id IN ( %s )", StringUtils.join(inClause, ","));
        if (sfdcIdsToAssignToBatch.size() > 0) {
            int restOfUpdatedRows = jdbcTemplate.update(insertTheRest, batchId);
            log("insertTheRest=" + insertTheRest);
            log("restOfUpdatedRows=" + restOfUpdatedRows);
        }


    }
}
