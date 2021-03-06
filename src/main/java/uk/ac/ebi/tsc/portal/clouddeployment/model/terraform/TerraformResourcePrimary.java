package uk.ac.ebi.tsc.portal.clouddeployment.model.terraform;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * @author Jose A. Dianes <jdianes@ebi.ac.uk>
 * @since v0.0.1
 * @author Navis Raj <navis@ebi.ac.uk>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Component
public class TerraformResourcePrimary {

    public String id;

    public Map<String, String> attributes;
}
