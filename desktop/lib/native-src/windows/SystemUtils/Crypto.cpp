#include "stdafx.h"
#include "SystemUtilities.h"
#include "Crypto.h"
#include <atlbase.h>
#include <Softpub.h>
#include <wincrypt.h>
#include <wintrust.h>

// Link with the Wintrust.lib file.
#pragma comment (lib, "wintrust")

#define ENCODING (X509_ASN_ENCODING | PKCS_7_ASN_ENCODING)

BOOL VerifyEmbeddedSignature(LPCSTR pwszSourceFile);

JNIEXPORT jboolean JNICALL Java_org_limewire_util_SystemUtils_verifyExecutableSignatureNative(JNIEnv *e, jclass c, jstring path, jbyteArray cert) {
	BOOL result = FALSE;
	LPCTSTR execPath = e->GetStringUTFChars(path, NULL);
	WCHAR unicodeExecPath[MAX_PATH];

	ZeroMemory(unicodeExecPath,MAX_PATH*sizeof(WCHAR));

	if (mbstowcs(unicodeExecPath, execPath, MAX_PATH) == -1) {
		e->ReleaseStringUTFChars(path,execPath);
		return FALSE; 
    }

	jbyte* bufferPtr = e->GetByteArrayElements(cert, NULL);
	jsize certLength = e->GetArrayLength(cert);

	PCCERT_CONTEXT expectedCertContext;
	PCCERT_CONTEXT executableCertContext;

	//Create Certificate out of expected certificate from memory
	expectedCertContext = CertCreateCertificateContext(ENCODING,
		(BYTE*) bufferPtr,
		certLength);

	if (expectedCertContext == NULL) {
		e->ReleaseStringUTFChars(path,execPath);
		e->ReleaseByteArrayElements(cert,bufferPtr,0);
		return FALSE;
	}

	HCERTSTORE hStore = NULL;
	CERT_INFO CertInfo;
	DWORD dwEncoding, dwContentType, dwFormatType;
	HCRYPTMSG hMsg = NULL;
	BOOL fResult;

	// Get message handle and store handle from the signed file.
	fResult = CryptQueryObject(CERT_QUERY_OBJECT_FILE,
		unicodeExecPath,
		CERT_QUERY_CONTENT_FLAG_PKCS7_SIGNED_EMBED,
		CERT_QUERY_FORMAT_FLAG_BINARY,
		0,
		&dwEncoding,
		&dwContentType,
		&dwFormatType,
		&hStore,
		&hMsg,
		NULL);

	if (fResult) {
		executableCertContext = CertFindCertificateInStore(hStore,
			ENCODING,
			0,
			CERT_FIND_SUBJECT_CERT,
			expectedCertContext->pCertInfo,//(PVOID)&CertInfo,
			NULL);
	} else {
	    _tprintf(_T("CryptQueryObject failed with %x\n"), GetLastError());
		executableCertContext = NULL;
	}

	result = executableCertContext != NULL;

	result = result && VerifyEmbeddedSignature(execPath);

	if (expectedCertContext != NULL) { CertFreeCertificateContext(expectedCertContext); }
	if (executableCertContext != NULL) { CertFreeCertificateContext(executableCertContext); }
	if (hStore != NULL) { CertCloseStore(hStore, 0); }
	if (hMsg != NULL) { CryptMsgClose(hMsg); }

	e->ReleaseStringUTFChars(path,execPath);
	e->ReleaseByteArrayElements(cert,bufferPtr,0);

	return result;
}

BOOL VerifyEmbeddedSignature(LPCSTR pzSourceFile)
{
	LPCWSTR pwszSourceFile;
	WCHAR wszSourceFile[MAX_PATH];
	ZeroMemory(wszSourceFile,MAX_PATH*sizeof(WCHAR));
	pwszSourceFile = wszSourceFile;

	if (mbstowcs(wszSourceFile, pzSourceFile, MAX_PATH) == -1) {
	    return FALSE;   
    }

    LONG lStatus;
    DWORD dwLastError;

    // Initialize the WINTRUST_FILE_INFO structure.

    WINTRUST_FILE_INFO FileData;
    memset(&FileData, 0, sizeof(FileData));
    FileData.cbStruct = sizeof(WINTRUST_FILE_INFO);
    FileData.pcwszFilePath = pwszSourceFile;
    FileData.hFile = NULL;
    FileData.pgKnownSubject = NULL;

    /*
    WVTPolicyGUID specifies the policy to apply on the file
    WINTRUST_ACTION_GENERIC_VERIFY_V2 policy checks:
    
    1) The certificate used to sign the file chains up to a root 
    certificate located in the trusted root certificate store. This 
    implies that the identity of the publisher has been verified by 
    a certification authority.
    
    2) In cases where user interface is displayed (which this example
    does not do), WinVerifyTrust will check for whether the  
    end entity certificate is stored in the trusted publisher store,  
    implying that the user trusts content from this publisher.
    
    3) The end entity certificate has sufficient permission to sign 
    code, as indicated by the presence of a code signing EKU or no 
    EKU.
    */

    GUID WVTPolicyGUID = WINTRUST_ACTION_GENERIC_VERIFY_V2;
    WINTRUST_DATA WinTrustData;

    // Initialize the WinVerifyTrust input data structure.

    // Default all fields to 0.
    memset(&WinTrustData, 0, sizeof(WinTrustData));

    WinTrustData.cbStruct = sizeof(WinTrustData);
    
    // Use default code signing EKU.
    WinTrustData.pPolicyCallbackData = NULL;

    // No data to pass to SIP.
    WinTrustData.pSIPClientData = NULL;

    // Disable WVT UI.
    WinTrustData.dwUIChoice = WTD_UI_NONE;

    // No revocation checking.
    WinTrustData.fdwRevocationChecks = WTD_REVOKE_NONE; 

    // Verify an embedded signature on a file.
    WinTrustData.dwUnionChoice = WTD_CHOICE_FILE;

    // Default verification.
    WinTrustData.dwStateAction = 0;

    // Not applicable for default verification of embedded signature.
    WinTrustData.hWVTStateData = NULL;

    // Not used.
    WinTrustData.pwszURLReference = NULL;

    // This is not applicable if there is no UI because it changes 
    // the UI to accommodate running applications instead of 
    // installing applications.
    WinTrustData.dwUIContext = 0;

    // Set pFile.
    WinTrustData.pFile = &FileData;

    // WinVerifyTrust verifies signatures as specified by the GUID 
    // and Wintrust_Data.
    lStatus = WinVerifyTrust(
        (HWND) INVALID_HANDLE_VALUE,
        &WVTPolicyGUID,
        &WinTrustData);

	BOOL result = FALSE;

    switch (lStatus) 
    {
        case ERROR_SUCCESS:
            /*
            Signed file:
                - Hash that represents the subject is trusted.

                - Trusted publisher without any verification errors.

                - UI was disabled in dwUIChoice. No publisher or 
                    time stamp chain errors.

                - UI was enabled in dwUIChoice and the user clicked 
                    "Yes" when asked to install and run the signed 
                    subject.
            */
            result = TRUE;
            break;
        
        case TRUST_E_NOSIGNATURE:
            // The file was not signed or had a signature 
            // that was not valid.
            result = FALSE;
            break;

        case TRUST_E_EXPLICIT_DISTRUST:
            // The hash that represents the subject or the publisher 
            // is not allowed by the admin or user.
            result = FALSE;
            break;

        case TRUST_E_SUBJECT_NOT_TRUSTED:
            // The user clicked "No" when asked to install and run.
            result = FALSE;
            break;

        case CRYPT_E_SECURITY_SETTINGS:
            /*
            The hash that represents the subject or the publisher 
            was not explicitly trusted by the admin and the 
            admin policy has disabled user trust. No signature, 
            publisher or time stamp errors.
            */
            result = FALSE;
            break;

        default:
            // The UI was disabled in dwUIChoice or the admin policy 
            // has disabled user trust. lStatus contains the 
            // publisher or time stamp chain error.
            result = FALSE;
            break;
    }

    return result;
}
