package com.signalspoc.connector.pm.linear.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LinearGraphQLResponse<T> {

    private DataWrapper<T> data;
    private List<GraphQLError> errors;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataWrapper<T> {
        private NodesWrapper<T> issues;
        private NodesWrapper<T> projects;
        private NodesWrapper<T> users;
        private NodesWrapper<T> comments;
        private T viewer;
        private T issue;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NodesWrapper<T> {
        private List<T> nodes;
        private PageInfo pageInfo;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageInfo {
        private boolean hasNextPage;
        private String endCursor;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GraphQLError {
        private String message;
        private List<Location> locations;
        private List<String> path;

        @Data
        public static class Location {
            private int line;
            private int column;
        }
    }
}
