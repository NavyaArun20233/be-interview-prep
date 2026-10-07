package com.interviewprep.link;

import com.interviewprep.common.error.ResourceGoneException;

public class ShortLinkExpiredException extends ResourceGoneException {

    public ShortLinkExpiredException(String code) {
        super("Short link " + code + " has expired");
    }
}
