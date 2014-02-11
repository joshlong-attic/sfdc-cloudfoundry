package demo.processors;

import org.apache.commons.lang3.time.DateUtils;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.Date;

@Component
class RestUtils {


    Date parseDate(Object o) {
        if (o instanceof String)
            return parseDate((String) o);
        return null;
    }

    Date parseDate(String input) {
        try {
            //ISO 8601 date
            return DateUtils.parseDate(input, "yyyy-MM-dd'T'HH:mm:ssZZ");
        } catch (ParseException e) {
            // throw new RuntimeException(e) ;
            return null;
        }
    }
}
