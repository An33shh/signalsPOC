package com.signalspoc.connector.pm.asana.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AsanaResponse<T> {

    private T data;

    @JsonProperty("next_page")
    private NextPage nextPage;

    @Data
    public static class NextPage {
        private String offset;
        private String uri;
    }
}
