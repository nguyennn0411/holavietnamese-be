package com.sep490.backend.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@Builder
public class RoleResponse {
    private Integer id;
    private String name;
    private String description;
    private Set<String> permissions;
}
