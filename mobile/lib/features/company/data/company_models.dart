/// Mirrors `company.dto.CompanyResponse` — the full self-service view
/// (`POST /companies/apply`'s response), including internal-only fields
/// {@link CompanyProfile} (the public view) never exposes.
class Company {
  const Company({
    required this.id,
    required this.displayName,
    required this.legalName,
    required this.registrationNumber,
    this.gstNumber,
    this.logoUrl,
    this.description,
    this.websiteUrl,
    required this.supportEmail,
    required this.supportPhone,
    required this.cancellationPolicy,
    required this.status,
    this.suspendedAt,
    this.suspensionReason,
    required this.createdAt,
  });

  factory Company.fromJson(Map<String, dynamic> json) => Company(
        id: json['id'] as String,
        displayName: json['display_name'] as String,
        legalName: json['legal_name'] as String,
        registrationNumber: json['registration_number'] as String,
        gstNumber: json['gst_number'] as String?,
        logoUrl: json['logo_url'] as String?,
        description: json['description'] as String?,
        websiteUrl: json['website_url'] as String?,
        supportEmail: json['support_email'] as String,
        supportPhone: json['support_phone'] as String,
        cancellationPolicy: json['cancellation_policy'] as String,
        status: json['status'] as String,
        suspendedAt: json['suspended_at'] as String?,
        suspensionReason: json['suspension_reason'] as String?,
        createdAt: json['created_at'] as String,
      );

  final String id;
  final String displayName;
  final String legalName;
  final String registrationNumber;
  final String? gstNumber;
  final String? logoUrl;
  final String? description;
  final String? websiteUrl;
  final String supportEmail;
  final String supportPhone;
  final String cancellationPolicy;

  /// `APPLICATION_SUBMITTED` / `UNDER_REVIEW` / `VERIFIED` / `SUSPENDED` / `REJECTED` / `REMOVED`.
  final String status;
  final String? suspendedAt;
  final String? suspensionReason;
  final String createdAt;
}

/// Mirrors `company.dto.CompanyProfileResponse` (`GET /companies/{id}`) — the
/// public Company Profile. Never carries `legalName`/`registrationNumber`/
/// `gstNumber` — those are verification-only fields on {@link Company}.
class CompanyProfile {
  const CompanyProfile({
    required this.id,
    required this.displayName,
    this.logoUrl,
    this.description,
    this.websiteUrl,
    required this.supportEmail,
    required this.supportPhone,
    required this.cancellationPolicy,
    required this.status,
    this.aggregateRating,
    required this.tripsCompletedCount,
  });

  factory CompanyProfile.fromJson(Map<String, dynamic> json) => CompanyProfile(
        id: json['id'] as String,
        displayName: json['display_name'] as String,
        logoUrl: json['logo_url'] as String?,
        description: json['description'] as String?,
        websiteUrl: json['website_url'] as String?,
        supportEmail: json['support_email'] as String,
        supportPhone: json['support_phone'] as String,
        cancellationPolicy: json['cancellation_policy'] as String,
        status: json['status'] as String,
        aggregateRating: (json['aggregate_rating'] as num?)?.toDouble(),
        tripsCompletedCount: json['trips_completed_count'] as int,
      );

  final String id;
  final String displayName;
  final String? logoUrl;
  final String? description;
  final String? websiteUrl;
  final String supportEmail;
  final String supportPhone;
  final String cancellationPolicy;
  final String status;

  /// Null until at least one Published review exists against one of this
  /// Company's trips — never fabricated as `0`.
  final double? aggregateRating;
  final int tripsCompletedCount;
}

/// Mirrors `company.dto.CompanyVerificationStatusResponse`.
class CompanyVerificationStatus {
  const CompanyVerificationStatus({required this.status, this.decisionNotes});

  factory CompanyVerificationStatus.fromJson(Map<String, dynamic> json) => CompanyVerificationStatus(
        status: json['status'] as String,
        decisionNotes: json['decision_notes'] as String?,
      );

  /// `UNDER_REVIEW` / `APPROVED` / `REJECTED`.
  final String status;
  final String? decisionNotes;
}

/// Mirrors `company.dto.CompanyUserResponse`.
class CompanyStaff {
  const CompanyStaff({
    required this.id,
    required this.companyId,
    required this.userId,
    required this.role,
    required this.status,
    required this.createdAt,
  });

  factory CompanyStaff.fromJson(Map<String, dynamic> json) => CompanyStaff(
        id: json['id'] as String,
        companyId: json['company_id'] as String,
        userId: json['user_id'] as String,
        role: json['role'] as String,
        status: json['status'] as String,
        createdAt: json['created_at'] as String,
      );

  final String id;
  final String companyId;
  final String userId;

  /// `OWNER` / `MANAGER` / `SUPPORT`.
  final String role;
  final String status;
  final String createdAt;
}

/// One entry of `POST /companies/apply`'s `documents: [...]` array — an
/// already-uploaded object-storage reference, same "no upload endpoint in
/// this app yet" pattern as trip images / profile photos.
class CompanyDocumentRef {
  const CompanyDocumentRef({required this.documentType, required this.storageKey});

  Map<String, dynamic> toJson() => {'document_type': documentType, 'storage_key': storageKey};

  final String documentType;
  final String storageKey;
}

/// `POST /companies/apply` request body.
class ApplyCompanyRequest {
  const ApplyCompanyRequest({
    required this.displayName,
    required this.legalName,
    required this.registrationNumber,
    this.gstNumber,
    required this.supportEmail,
    required this.supportPhone,
    required this.cancellationPolicy,
    required this.documents,
  });

  final String displayName;
  final String legalName;
  final String registrationNumber;
  final String? gstNumber;
  final String supportEmail;
  final String supportPhone;
  final String cancellationPolicy;
  final List<CompanyDocumentRef> documents;

  Map<String, dynamic> toJson() => {
        'display_name': displayName,
        'legal_name': legalName,
        'registration_number': registrationNumber,
        'gst_number': gstNumber,
        'support_email': supportEmail,
        'support_phone': supportPhone,
        'cancellation_policy': cancellationPolicy,
        'documents': documents.map((d) => d.toJson()).toList(),
      };
}
