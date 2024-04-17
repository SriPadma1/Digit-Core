package org.egov.sunbirdrc.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Component
public class CredentialIdUuidMapper {

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("vcid")
    private String vcid;

    private String createdBy;

}