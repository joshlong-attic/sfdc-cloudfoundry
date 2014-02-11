package demo;

import com.force.api.ForceApi;
import com.force.api.Identity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * @author Josh Long (josh@joshlong.com)
 */
@Controller
class SfdcMvcController {

    @Autowired
    ForceApi forceApi;

    @RequestMapping(value = "/", method = RequestMethod.GET)
    String contacts(Model model) {
        Identity identity = this.forceApi.getIdentity();
        model.addAttribute("id", identity.getId());
        return "maps";
    }
}
