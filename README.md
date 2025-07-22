Java Manga Translator
This is a command-line tool written in Java that automatically translates text in manga or comic book images. It uses the free-tier OCR.space API to detect Japanese text and the free MyMemory Translation API to translate it into English.

The tool processes an input image, "cleans" the original text by covering it with white boxes, and then renders the translated English text in its place.

Features
Automated OCR: Detects text directly from image files.

Machine Translation: Translates Japanese to English.

Image Cleaning: Automatically covers original text bubbles.

Dynamic Text Rendering: Adjusts font size to fit the translated text neatly into the original space.

Optimized for Manga: Uses OCR Engine 5 and other parameters tailored for Japanese text and varied layouts.

How to Use
Prerequisites
Java Development Kit (JDK) 11 or higher installed.

A free API key from OCR.space.

Setup
Clone the Repository

git clone [https://github.com/YOUR_USERNAME/java-manga-translator.git](https://github.com/YOUR_USERNAME/java-manga-translator.git)
cd java-manga-translator

Add Your API Key

Open the MangaTranslator.java file in a text editor.

Find the line: private static final String OCR_SPACE_API_KEY = "YOUR_OCR_SPACE_API_KEY_HERE";

Replace "YOUR_OCR_SPACE_API_KEY_HERE" with your actual API key from OCR.space.

Running the Translator
Compile the Code
From the project's root directory, run the following command:

javac MangaTranslator.java

Execute the Program
Run the program from the command line, providing the path to your input image and a desired name for the output file.

java MangaTranslator <path_to_input_image.jpg> <path_for_output_image.png>

Example:

java MangaTranslator my_manga/page_01.jpg translated/page_01_translated.png

The translated image will be saved to the output path you specified.

How It Works
Image Loading: The program loads the source image into a BufferedImage.

API Request: The image is encoded in Base64 and sent to the OCR.space API via an HTTP POST request, along with parameters optimized for Japanese manga.

JSON Parsing: The API's JSON response, containing text and word coordinates, is manually parsed to create a list of TranslationUnit objects.

Translation: Each line of detected text is sent to the MyMemory API for translation.

Image Manipulation: The AWT (Abstract Window Toolkit) library is used to create a new, cleaned image and draw the translated text onto it.

Saving: The final BufferedImage is written to a new PNG file.
