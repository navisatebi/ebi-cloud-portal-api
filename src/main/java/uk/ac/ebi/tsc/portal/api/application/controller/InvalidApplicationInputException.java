package uk.ac.ebi.tsc.portal.api.application.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * @author Jose A. Dianes <jdianes@ebi.ac.uk>
 * @since v0.0.1
 * @author Navis Raj <navis@ebi.ac.uk>
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class InvalidApplicationInputException extends RuntimeException {
	
	public InvalidApplicationInputException(String username, String name) {
        super("Could not create application '" + name + "' for user '" + username + "'. Invalid input.");
    }
	
	public InvalidApplicationInputException(){
		super("Either application name/application owner's account username or both are missing");
	}

}
