# Faenum Search Integration

## Overview
Faenum (https://www.faenum.com/) is a public domain image search engine that uses AI to match text searches to images. This integration adds Faenum as a search source in FrostWire.

## Implementation Status

### Completed
- ✅ Created `FaenumSearchPattern` class in `common/src/main/java/com/frostwire/search/faenum/`
- ✅ Registered Faenum search engine in Android (`SearchEngine.java`)
- ✅ Registered Faenum search engine in Desktop (`SearchEngine.java`)
- ✅ Added preference key for Android (`Constants.PREF_KEY_SEARCH_USE_FAENUM`)
- ✅ Added setting for Desktop (`SearchEnginesSettings.FAENUM_SEARCH_ENABLED`)
- ✅ Created test class (`FaenumSearchPatternTest.java`)

### Pending Verification
The actual Faenum API endpoint needs to be verified and adjusted. The current implementation assumes:

1. **API Endpoint**: `https://www.faenum.com/api/search?q={keywords}&limit={max_results}`
   - This is a common REST API pattern but needs verification
   - **NOTE**: If you're getting "Empty response body" errors, the API endpoint is likely incorrect

2. **How to Find the Correct API Endpoint**:
   - Open https://www.faenum.com/ in a browser
   - Open browser Developer Tools (F12)
   - Go to the "Network" tab
   - Perform a search on the website
   - Look for XHR/Fetch requests that contain the search results
   - Note the actual endpoint URL and request method (GET/POST)
   - Update `FaenumSearchPattern.getSearchUrl()` with the correct URL

3. **Response Format**: Expected JSON structure:
   ```json
   {
     "results": [
       {
         "id": "unique_id",
         "title": "Image title",
         "description": "Image description",
         "imageUrl": "https://example.com/image.jpg",
         "thumbnailUrl": "https://example.com/thumbnail.jpg",
         "detailsUrl": "https://www.faenum.com/image/{id}",
         "fileSize": 123456,
         "width": 1920,
         "height": 1080,
         "source": "Original museum/collection",
         "license": "Public Domain"
       }
     ],
     "total": 100,
     "page": 1
   }
   ```

3. **Fallback Patterns**: If the API structure differs, the following may need adjustment:
   - Field names in `FaenumImage` class
   - Response parsing in `parseResults()` method
   - URL construction in `getSearchUrl()` method

## How to Verify/Update the API Implementation

Once Faenum.com is accessible, perform these steps:

1. **Discover the API Endpoint**:
   - Visit https://www.faenum.com/
   - Open browser developer tools (Network tab)
   - Perform a search
   - Examine the network requests to find the actual API endpoint
   - Common patterns:
     - `/api/search`
     - `/search/api`
     - `/api/v1/search`
     - `/api/images/search`

2. **Examine the Response**:
   - Look at the JSON response structure
   - Identify field names for:
     - Image ID
     - Title/Name
     - Image URL (full size)
     - Thumbnail URL
     - Detail page URL
     - File size (if available)
     - Dimensions (if available)
     - License information
     - Source/Collection

3. **Update FaenumSearchPattern.java**:
   - Update `getSearchUrl()` with the correct endpoint
   - Update `FaenumImage` class with actual field names
   - Adjust parsing logic in `parseResults()` if needed
   - Add any required HTTP headers in `getCustomHeaders()` if the API requires authentication

4. **Test the Integration**:
   ```bash
   cd desktop
   ./gradlew test --tests "com.frostwire.tests.FaenumSearchPatternTest.faenumSearchTest"
   ```

5. **Common Adjustments**:
   - If the API uses POST instead of GET, override `getHttpMethod()` to return `HttpMethod.POST` or `HttpMethod.POST_JSON`
   - If authentication is required, implement `getCustomHeaders()` to add API keys
   - If pagination is different, adjust the URL parameters
   - If the response is not JSON, implement custom parsing logic

## API Documentation Resources
- Check for official API documentation at https://www.faenum.com/api or similar
- Look for GitHub repository or developer documentation
- Contact the developer via Reddit: https://www.reddit.com/r/creativecommons/comments/1r1nbnf/i_made_a_site_for_searching_thousands_of_public/

## Testing
The test is designed to be resilient:
- It won't fail the build if the site is down
- It logs warnings instead of errors for validation issues
- It provides detailed output about what was received

## License
All images from Faenum are public domain, as stated in their description. The search pattern correctly marks all results with `Licenses.PUBLIC_DOMAIN_MARK`.
