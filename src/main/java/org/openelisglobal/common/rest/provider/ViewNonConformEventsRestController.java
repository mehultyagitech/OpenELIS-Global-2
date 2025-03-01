package org.openelisglobal.common.rest.provider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.openelisglobal.common.rest.bean.ViewNonConformEventsResponse;
import org.openelisglobal.common.services.DisplayListService;
import org.openelisglobal.common.util.DateUtil;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.openelisglobal.qaevent.form.NonConformingEventForm;
import org.openelisglobal.qaevent.service.NCEventService;
import org.openelisglobal.qaevent.service.NceCategoryService;
import org.openelisglobal.qaevent.service.NceSpecimenService;
import org.openelisglobal.qaevent.service.NceTypeService;
import org.openelisglobal.qaevent.valueholder.NcEvent;
import org.openelisglobal.qaevent.valueholder.NceSpecimen;
import org.openelisglobal.qaevent.worker.NonConformingEventWorker;
import org.openelisglobal.sampleitem.service.SampleItemService;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.openelisglobal.systemuser.service.SystemUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ViewNonConformEventsRestController {

  @Autowired
  private NCEventService ncEventService;

  @Autowired
  private NceCategoryService nceCategoryService;

  @Autowired
  private NceTypeService nceTypeService;

  @Autowired
  private NceSpecimenService nceSpecimenService;

  @Autowired
  private SampleItemService sampleItemService;

  @Autowired
  private SystemUserService systemUserService;

  private static final String USER_SESSION_DATA = "userSessionData";

  private final NonConformingEventWorker nonConformingEventWorker;

  public ViewNonConformEventsRestController(
    NonConformingEventWorker nonConformingEventWorker
  ) {
    this.nonConformingEventWorker = nonConformingEventWorker;
  }

  @GetMapping(value = "/rest/viewNonConformEvents")
  public ResponseEntity<?> getNceNumber(
    @RequestParam(required = false) String labNumber,
    @RequestParam(required = false) String nceNumber,
    @RequestParam(required = false) String status,
    HttpServletRequest request
  ) {
    Map<String, Object> searchParameters = new HashMap<>();
    searchParameters.put("status", status);
    List<NcEvent> searchResults = new ArrayList<>();
    if (!"".equalsIgnoreCase(labNumber)) {
      searchParameters.put("labOrderNumber", labNumber);
    } else if (!"".equalsIgnoreCase(nceNumber)) {
      searchParameters.put("nceNumber", nceNumber);
    }

    searchResults = ncEventService.getAllMatching(searchParameters);

    if (searchResults.size() == 0) {
      return ResponseEntity.ok().body("No results found for search criteria.");
    }

    String newNceNumber = searchResults.get(0).getNceNumber();

    NcEvent event = ncEventService.getMatch("nceNumber", newNceNumber).get();

    ViewNonConformEventsResponse response = new ViewNonConformEventsResponse();

    response.setResults(searchResults);
    response.setNceCategories(nceCategoryService.getAllNceCategories());
    response.setNceTypes(nceTypeService.getAllNceTypes());
    response.setLabComponentList(
      DisplayListService.getInstance()
        .getList(DisplayListService.ListType.LABORATORY_COMPONENT)
    );
    response.setSeverityConsequenceList(
      DisplayListService.getInstance()
        .getList(DisplayListService.ListType.SEVERITY_CONSEQUENCES_LIST)
    );
    response.setReportingUnits(
      DisplayListService.getInstance()
        .getList(DisplayListService.ListType.TEST_SECTION_ACTIVE)
    );
    response.setSeverityRecurs(
      DisplayListService.getInstance()
        .getList(DisplayListService.ListType.SEVERITY_RECURRENCE_LIST)
    );
    response.setRepoUnit(event.getReportingUnitId());
    response.setCurrentUserId(
      systemUserService.getUserById(getSysUserId(request))
    );

    List<NceSpecimen> specimenList = nceSpecimenService.getAllMatching(
      "nceId",
      event.getId()
    );
    List<SampleItem> sampleItems = new ArrayList<>();
    for (NceSpecimen specimen : specimenList) {
      SampleItem si = sampleItemService.getData(
        specimen.getSampleItemId() + ""
      );
      sampleItems.add(si);
    }
    response.setSpecimen(sampleItems);
    response.setReportDate(DateUtil.formatDateAsText(event.getReportDate()));
    response.setDateOfEvent(DateUtil.formatDateAsText(event.getDateOfEvent()));

    return ResponseEntity.ok().body(response);
  }

  @PostMapping(
    value = "/rest/viewNonConformEvents",
    produces = MediaType.APPLICATION_JSON_VALUE
  )
  public ResponseEntity<?> postReportNonConformingEvent(
    @RequestBody NonConformingEventForm form
  ) {
    try {
      boolean updated = nonConformingEventWorker.update(form);
      if (updated) {
        return ResponseEntity.ok().body(Map.of("success", true));
      } else {
        return ResponseEntity.ok().body(Map.of("success", false));
      }
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
        "An error occurred while processing the request." + e
      );
    }
  }

  protected String getSysUserId(HttpServletRequest request) {
    UserSessionData usd = (UserSessionData) request
      .getSession()
      .getAttribute(USER_SESSION_DATA);
    if (usd == null) {
      usd = (UserSessionData) request.getAttribute(USER_SESSION_DATA);
      if (usd == null) {
        return null;
      }
    }
    return String.valueOf(usd.getSystemUserId());
  }
}
