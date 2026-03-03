@edi
Feature: EDI outbound verification

  # ─── Single inbound / single outbound ──────────────────────────────────────

  Scenario: Booking creates a minimum 304 IFT outbound
    When I drop below input files
      | SI_Min_Edifact | CU2100_Inbound |
    And I wait for 30 Sec
    Then I verify below outbounds
      | Template Name  | Location                  |
      | SI_Min_Edifact | CA2000_304IFT_Min_Archieve |

  # ─── Multiple inbound files, same outbound ──────────────────────────────────

  Scenario: Multiple bookings each produce a 304 IFT outbound
    When I drop below input files
      | SI_Min_Edifact_001 | CU2100_Inbound |
      | SI_Min_Edifact_002 | CU2100_Inbound |
    And I wait for 60 Sec
    Then I verify below outbounds
      | Template Name  | Location                  |
      | SI_Min_Edifact | CA2000_304IFT_Min_Archieve |

  # ─── Multiple outbound checks ───────────────────────────────────────────────

  Scenario: Booking creates both an acknowledgement and a 304 IFT outbound
    When I drop below input files
      | SI_Min_Edifact | CU2100_Inbound |
    And I wait for 45 Sec
    Then I verify below outbounds
      | Template Name        | Location                  |
      | SI_Min_Edifact       | CA2000_304IFT_Min_Archieve |
      | SI_Min_Edifact_Ack   | CA2000_304IFT_Min_Archieve |
