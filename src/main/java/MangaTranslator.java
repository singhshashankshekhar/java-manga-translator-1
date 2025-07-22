import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A Manga Translation Tool that uses free-tier APIs for OCR and Translation.
 *
 * This program uses:
 * 1. OCR.space API for Optical Character Recognition.
 * 2. MyMemory API for machine translation.
 *
 * Workflow:
 * 1. Takes an input and output file path from the command line.
 * 2. Calls the OCR.space API to detect text.
 * 3. Calls the MyMemory API to translate the detected text.
 * 4. Cleans the original image by painting over the text regions.
 * 5. Saves the final image to the specified output path.
 *
 * NOTE: To run this, you must:
 * 1. Get a free API Key from https://ocr.space/ and replace the placeholder.
 * 2. A proper JSON parsing library (like Gson or Jackson) is recommended for
 * production code, but this example uses basic string manipulation to
 * remain self-contained.
 */
public class MangaTranslator {

    // --- CONFIGURATION ---
    // IMPORTANT: Get a free API key from https://ocr.space/ and paste it here.
    private static final String OCR_SPACE_API_KEY = "YOUR_OCR_SPACE_API_KEY_HERE";
    private static final String OCR_API_URL = "https://api.ocr.space/parse/image";
    private static final String TRANSLATE_API_URL = "https://api.mymemory.translated.net/get";

    /**
     * A helper class to hold all information about a piece of text.
     */
    private static class TranslationUnit {
        final Rectangle region;
        final String originalText;
        String translatedText;

        TranslationUnit(Rectangle region, String originalText) {
            this.region = region;
            this.originalText = originalText;
        }
    }

    // --- Main Application Workflow ---

    /**
     * Main method to run the translation process.
     *
     * @param args Command-line arguments: <input_image_path> <output_image_path>
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java MangaTranslator <input_image_path> <output_image_path>");
            return;
        }
        if (OCR_SPACE_API_KEY.equals("YOUR_OCR_SPACE_API_KEY_HERE")) {
            System.err.println(
                    "Error: Please get a free API key from https://ocr.space/ and replace the placeholder in the code.");
            return;
        }

        String inputPath = args[0];
        String outputPath = args[1];

        System.out.println("Starting Manga Translation Process for: " + inputPath);

        try {
            File inputFile = new File(inputPath);
            if (!inputFile.exists()) {
                System.err.println("Error: Input file not found: " + inputPath);
                return;
            }

            // Warn if the file is larger than 1MB, as this can cause issues.
            if (inputFile.length() > 1024 * 1024) {
                System.err.println("Warning: Input file size is over 1MB (" + inputFile.length() / 1024
                        + " KB). This may exceed the API's free tier limit and cause timeouts or errors.");
            }

            BufferedImage image = ImageIO.read(inputFile);

            // 1. Call OCR API to get all text annotations
            System.out.println("Step 1: Detecting text with OCR.space API...");
            List<TranslationUnit> units = detectText(image);
            if (units.isEmpty()) {
                System.out.println("No text detected. Exiting.");
                return;
            }
            System.out.println("Detected " + units.size() + " text fragments.");

            // 2. Translate each text fragment
            System.out.println("Step 2: Translating text with MyMemory API...");
            for (TranslationUnit unit : units) {
                // Clean up text before translating to remove artifacts
                String cleanText = unit.originalText.replaceAll("[/′:・C【】１２\\-〃」]", "").trim();
                if (!cleanText.isEmpty()) {
                    unit.translatedText = translateText(cleanText, "ja", "en");
                    System.out.println("  '" + unit.originalText + "' -> '" + unit.translatedText + "'");
                } else {
                    unit.translatedText = "";
                }
            }

            // 3. Clean the image by removing original text
            System.out.println("Step 3: Cleaning original text from image...");
            List<Rectangle> regions = new ArrayList<>();
            for (TranslationUnit unit : units) {
                regions.add(unit.region);
            }
            BufferedImage cleanedImage = cleanImage(image, regions);

            // 4. Render translated text onto the cleaned image
            System.out.println("Step 4: Rendering translated text onto image...");
            for (TranslationUnit unit : units) {
                if (unit.translatedText != null && !unit.translatedText.isEmpty()) {
                    renderTranslatedText(cleanedImage, unit.region, unit.translatedText);
                }
            }

            // 5. Save the final image
            File outputFile = new File(outputPath);
            ImageIO.write(cleanedImage, "png", outputFile);
            System.out.println("\nProcess complete. Translated image saved as '" + outputPath + "'");

        } catch (Exception e) {
            System.err.println("An error occurred during the translation process.");
            e.printStackTrace();
        }
    }

    /**
     * Calls the OCR.space API to detect text in an image.
     */
    public static List<TranslationUnit> detectText(BufferedImage image) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", byteArrayOutputStream);
        byte[] imageBytes = byteArrayOutputStream.toByteArray();
        String base64Image = "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(imageBytes);

        URL url = new URL(OCR_API_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(60000); // 60 seconds
        connection.setReadTimeout(60000); // 60 seconds
        connection.setRequestMethod("POST");

        String boundary = "---" + UUID.randomUUID().toString();
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setDoOutput(true);

        try (DataOutputStream request = new DataOutputStream(connection.getOutputStream())) {
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"apikey\"\r\n\r\n");
            request.writeBytes(OCR_SPACE_API_KEY + "\r\n");

            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"language\"\r\n\r\n");
            request.writeBytes("jpn\r\n");

            // Use OCR Engine 5, which is a newer and often better engine
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"OCREngine\"\r\n\r\n");
            request.writeBytes("5\r\n");

            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"isOverlayRequired\"\r\n\r\n");
            request.writeBytes("true\r\n");

            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"detectOrientation\"\r\n\r\n");
            request.writeBytes("true\r\n");

            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"scale\"\r\n\r\n");
            request.writeBytes("true\r\n");

            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"base64Image\"\r\n\r\n");
            request.writeBytes(base64Image + "\r\n");

            request.writeBytes("--" + boundary + "--\r\n");
            request.flush();
        }

        int responseCode = connection.getResponseCode();
        StringBuilder response = new StringBuilder();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                (responseCode >= 200 && responseCode <= 299) ? connection.getInputStream()
                        : connection.getErrorStream(),
                StandardCharsets.UTF_8))) {
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }

        connection.disconnect();
        System.out.println("DEBUG: Raw OCR.space API Response:\n" + response.toString());

        if (responseCode >= 200 && responseCode <= 299) {
            return parseOcrSpaceResponse(response.toString());
        } else {
            throw new IOException(
                    "OCR.space API returned HTTP " + responseCode + " with message: " + response.toString());
        }
    }

    /**
     * Calls the MyMemory Translation API.
     */
    public static String translateText(String text, String sourceLang, String targetLang) throws IOException {
        String urlStr = String.format("%s?q=%s&langpair=%s|%s",
                TRANSLATE_API_URL,
                URLEncoder.encode(text, "UTF-8"),
                sourceLang,
                targetLang);

        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        StringBuilder response = new StringBuilder();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                (responseCode >= 200 && responseCode <= 299) ? connection.getInputStream()
                        : connection.getErrorStream(),
                StandardCharsets.UTF_8))) {
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }

        connection.disconnect();

        if (responseCode >= 200 && responseCode <= 299) {
            return parseMyMemoryResponse(response.toString());
        } else {
            throw new IOException(
                    "MyMemory API returned HTTP " + responseCode + " with message: " + response.toString());
        }
    }

    /**
     * Cleans the detected text regions from the image.
     */
    public static BufferedImage cleanImage(BufferedImage originalImage, List<Rectangle> regions) {
        BufferedImage cleanedImage = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(),
                originalImage.getType());
        Graphics2D g = cleanedImage.createGraphics();
        g.drawImage(originalImage, 0, 0, null);
        g.setColor(Color.WHITE);
        for (Rectangle region : regions) {
            g.fill(region);
        }
        g.dispose();
        return cleanedImage;
    }

    /**
     * Renders the translated text onto the image.
     */
    public static void renderTranslatedText(BufferedImage image, Rectangle region, String text) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.BLACK);

        int fontSize = region.height;
        Font font;
        do {
            font = new Font("SansSerif", Font.BOLD, fontSize--);
            g.setFont(font);
        } while (g.getFontMetrics().stringWidth(text) > region.width && fontSize > 6);

        int x = region.x + (region.width - g.getFontMetrics().stringWidth(text)) / 2;
        int y = region.y + (region.height - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
        g.drawString(text, x, y);
        g.dispose();
    }

    // --- UTILITY AND PARSING METHODS ---

    /**
     * A more robust parser for the OCR.space JSON response.
     */
    private static List<TranslationUnit> parseOcrSpaceResponse(String json) throws IOException {
        if (json.contains("\"IsErroredOnProcessing\":true")) {
            String errorMessage = extractJsonValue(json, "ErrorMessage", 0);
            if (errorMessage == null || errorMessage.isEmpty()) {
                errorMessage = "API returned a processing error.";
            }
            throw new IOException("OCR.space API reported an error: " + errorMessage);
        }

        List<TranslationUnit> units = new ArrayList<>();

        String linesKey = "\"Lines\":[";
        int linesStartIndex = json.indexOf(linesKey);
        if (linesStartIndex == -1)
            return units;

        int linesEndIndex = findMatchingBrace(json, json.indexOf('[', linesStartIndex));
        if (linesEndIndex == -1)
            return units;

        String linesContent = json.substring(linesStartIndex + linesKey.length(), linesEndIndex);

        int lineCursor = 0;
        System.out.println("DEBUG: Starting to parse line objects.");
        while (lineCursor < linesContent.length()) {
            int lineStart = linesContent.indexOf('{', lineCursor);
            if (lineStart == -1)
                break;

            int lineEnd = findMatchingBrace(linesContent, lineStart);
            if (lineEnd == -1)
                break;

            String lineObjectStr = linesContent.substring(lineStart, lineEnd + 1);
            lineCursor = lineEnd + 1;
            System.out.println("DEBUG: Parsing line object: " + lineObjectStr);

            try {
                String text = extractJsonValue(lineObjectStr, "LineText", 0);
                if (text == null)
                    continue;
                System.out.println("DEBUG: Extracted text: " + text);

                String wordsKey = "\"Words\":[";
                int wordsStartIndex = lineObjectStr.indexOf(wordsKey);
                if (wordsStartIndex == -1)
                    continue;

                int wordsEndIndex = findMatchingBrace(lineObjectStr, lineObjectStr.indexOf('[', wordsStartIndex));
                if (wordsEndIndex == -1)
                    continue;

                String wordsContent = lineObjectStr.substring(wordsStartIndex + wordsKey.length(), wordsEndIndex);

                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
                int maxX = 0, maxY = 0;
                boolean hasWords = false;

                int wordCursor = 0;
                while (wordCursor < wordsContent.length()) {
                    int wordStart = wordsContent.indexOf('{', wordCursor);
                    if (wordStart == -1)
                        break;
                    int wordEnd = findMatchingBrace(wordsContent, wordStart);
                    if (wordEnd == -1)
                        break;

                    String wordObjectStr = wordsContent.substring(wordStart, wordEnd + 1);
                    wordCursor = wordEnd + 1;

                    int left = (int) Double.parseDouble(extractJsonValue(wordObjectStr, "Left", 0));
                    int top = (int) Double.parseDouble(extractJsonValue(wordObjectStr, "Top", 0));
                    int width = (int) Double.parseDouble(extractJsonValue(wordObjectStr, "Width", 0));
                    int height = (int) Double.parseDouble(extractJsonValue(wordObjectStr, "Height", 0));

                    minX = Math.min(minX, left);
                    minY = Math.min(minY, top);
                    maxX = Math.max(maxX, left + width);
                    maxY = Math.max(maxY, top + height);
                    hasWords = true;
                }

                if (hasWords) {
                    System.out.println("DEBUG: Successfully created TranslationUnit for text: " + text);
                    units.add(new TranslationUnit(new Rectangle(minX, minY, maxX - minX, maxY - minY), text));
                }
            } catch (Exception e) {
                System.err.println("DEBUG: Error parsing a line segment, skipping. Details: " + e.getMessage());
            }
        }
        System.out.println("DEBUG: Finished parsing. Found " + units.size() + " units in total.");
        return units;
    }

    private static int findMatchingBrace(String text, int openBraceIndex) {
        int braceCount = 1;
        char openBrace = text.charAt(openBraceIndex);
        char closeBrace = (openBrace == '{') ? '}' : ']';

        for (int i = openBraceIndex + 1; i < text.length(); i++) {
            if (text.charAt(i) == openBrace)
                braceCount++;
            if (text.charAt(i) == closeBrace)
                braceCount--;
            if (braceCount == 0)
                return i;
        }
        return -1; // Not found
    }

    private static String parseMyMemoryResponse(String json) {
        try {
            return extractJsonValue(json, "translatedText", 0);
        } catch (Exception e) {
            return "[Translation Error]";
        }
    }

    private static String extractJsonValue(String json, String key, int startIndex) {
        String searchKey = "\"" + key + "\":";
        int keyIndex = json.indexOf(searchKey, startIndex);
        if (keyIndex == -1)
            return null;

        int valueStart = keyIndex + searchKey.length();

        if (json.charAt(valueStart) == '"') { // It's a string
            valueStart++;
            int valueEnd = json.indexOf('"', valueStart);
            if (valueEnd == -1)
                return null;
            return json.substring(valueStart, valueEnd).replace("\\r\\n", " ").trim();
        } else { // It's a number or boolean
            StringBuilder sb = new StringBuilder();
            for (int i = valueStart; i < json.length(); i++) {
                char c = json.charAt(i);
                if (Character.isDigit(c) || c == '.') {
                    sb.append(c);
                } else {
                    break;
                }
            }
            return sb.toString();
        }
    }
}
