package digit.service;

import digit.repository.BoundaryHierarchyRepository;
import digit.service.enrichment.BoundaryHierarchyEnricher;
import digit.service.validator.BoundaryHierarchyValidator;
import digit.web.models.*;
import org.egov.common.utils.ResponseInfoUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import java.util.List;

@Service
public class BoundaryHierarchyDefinitionService {

    private BoundaryHierarchyValidator boundaryHierarchyValidator;

    private BoundaryHierarchyEnricher boundaryHierarchyEnricher;

    private BoundaryHierarchyRepository boundaryHierarchyRepository;

    @Autowired
    public BoundaryHierarchyDefinitionService(BoundaryHierarchyValidator boundaryHierarchyValidator, BoundaryHierarchyEnricher boundaryHierarchyEnricher,
                                              BoundaryHierarchyRepository boundaryHierarchyRepository) {
        this.boundaryHierarchyValidator = boundaryHierarchyValidator;
        this.boundaryHierarchyEnricher = boundaryHierarchyEnricher;
        this.boundaryHierarchyRepository = boundaryHierarchyRepository;
    }

    /**
     * Method for processing boundary hierarchy create requests.
     * @param body
     * @return
     */
    public BoundaryTypeHierarchyResponse createBoundaryHierarchyDefinition(BoundaryTypeHierarchyRequest body) {

        // Validate boundary hierarchy
        boundaryHierarchyValidator.validateBoundaryTypeHierarchy(body);

        // Enrich boundary hierarchy
        boundaryHierarchyEnricher.enrichBoundaryHierarchyDefinition(body);

        // Delegate request to boundary repository
        boundaryHierarchyRepository.create(body);

        // Build response and return
        return BoundaryTypeHierarchyResponse.builder()
                .boundaryHierarchy(body.getBoundaryHierarchy())
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(body.getRequestInfo(), Boolean.TRUE))
                .build();
    }

    /**
     * Method for processing boundary hierarchy definition search requests.
     * @param body
     * @return
     */
    public BoundaryTypeHierarchyResponse searchBoundaryHierarchyDefinition(BoundaryTypeHierarchySearchRequest body) {

        // Search for boundary hierarchy depending on the provided search criteria
        List<BoundaryTypeHierarchyDefinition> boundaryTypeHierarchyDefinitionList = boundaryHierarchyRepository.search(body.getBoundaryTypeHierarchySearchCriteria());

        // Set boundary hierarchy definition as null if not found
        BoundaryTypeHierarchyDefinition boundaryTypeHierarchyDefinition = CollectionUtils.isEmpty(boundaryTypeHierarchyDefinitionList) ? null : boundaryTypeHierarchyDefinitionList.get(0);

        // Build response and return
        return BoundaryTypeHierarchyResponse.builder()
                .boundaryHierarchy(boundaryTypeHierarchyDefinition)
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(body.getRequestInfo(), Boolean.TRUE))
                .build();
    }

}