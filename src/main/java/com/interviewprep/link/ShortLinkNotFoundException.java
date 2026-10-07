package com.interviewprep.link;

import com.interviewprep.common.error.ResourceNotFoundException;

public class ShortLinkNotFoundException extends ResourceNotFoundException {

    public ShortLinkNotFoundException(String code) {
        super("Short link " + code + " not found");
    }
}
