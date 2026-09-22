/**
 * File Service: tenant prefix via {@code FileObjectKeyGuard}, file_id metadata, signed URL
 * second authorization before S3. Object bytes live behind {@link one.nexus.file.ObjectStoragePort}.
 * S3 access/secret keys are retrieved only via {@code SecretPort} (local+env; no OpenBao/KMS).
 */
package one.nexus.file;
