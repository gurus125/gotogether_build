package com.gotogether.storage.dto;

/**
 * {@code uploadUrl} is a presigned S3 PUT URL the client uploads the raw
 * image bytes to directly (no bytes ever pass through this backend).
 * {@code publicUrl} is the URL to persist afterwards (e.g. as {@code
 * UpdateProfileRequest.photoUrl}, or in a {@code POST /trips/{id}/images}
 * call) — a stable, non-expiring URL either way, but its actual shape
 * depends on {@code gotogether.storage.serve-via-proxy} (see {@code
 * StorageProperties}' class doc): a raw unsigned bucket URL if the bucket
 * is public-read (local MinIO), or this backend's own {@code
 * /storage/view?key=...} redirect endpoint if it isn't (e.g. Railway
 * Storage Buckets, which don't support public buckets at all).
 */
public record PresignedUploadResponse(String uploadUrl, String publicUrl, String key) {}
