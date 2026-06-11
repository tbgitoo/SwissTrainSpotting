# Module 1 — Android App UI Implementation Plan

## 1. Module Purpose
Create the foundational visual shell and navigation flow for the project. The goal is to establish a working two-screen workflow in Java/XML:
1.  **MainActivity**: A central hub displaying the app title and two trigger buttons ("Image", "Camera").
2.  **ImageClassificationActivity**: A downstream screen that acts as a placeholder to verify the navigation stack works and displays a static demo image. 

No actual image capturing or model inference logic is included in this module; this module focuses strictly on layout rendering, window insets (system bars), and Activity lifecycle management.

## 2. UI Structure

### MainActivity (Module Home)
- **Root**: `LinearLayout` (Vertical orientation, gravity center) inside a `CoordinatorLayout`.
- **Header**: A `TextView` displaying "SwissTrainSpotting" in a large, bold style (`TextAppearance.Material.Title`).
- **Controls**: A `LinearLayout` (Horizontal or Vertical depending on screen aspect ratio) containing two symmetric `MaterialButton`s:
  - Button A: "Image"
  - Button B: "Camera"
- **Layout Behavior**: Everything centered vertically and horizontally; safe for all screen sizes.

### ImageClassificationActivity (The Viewer)
- **Root**: `AndroidX ConstraintLayout`.
- **Content**: A single large `ImageView` (`match_parent`) with a placeholder stock drawable (e.g., an abstract shape or simple scenery photo).
- **System Bars**: Implemented `EdgeToEdge` logic so the image safely extends under system status/navigation bars without being obscured.

## 3. Activity Structure

1.  **MainActivity** (Entry Point)
    - Loads `activity_main.xml`.
    - Configures window insets using `ViewCompat` to ensure UI elements don't overlap system bars (standard Android requirement).
2.  **ImageClassificationActivity** (Target)
    - Loads `activity_image_classification.xml`.
    - Sets a hardcoded placeholder image resource via `setImageResource()`.
    - Displays a standard System Back arrow in the top-left corner for navigation back to `MainActivity`.

## 4. Files to Create or Modify

### Existing Files (Modify)
1.  **`app/src/main/java/com/tb/swisstrainspotting/MainActivity.java`**
    - *Action*: Add click listeners to buttons that launch the new activity. Ensure XML IDs match the buttons added in step 2 below.
2.  **`app/src/main/res/values/themes.xml`** (Implicit)
    - Ensure `Theme.SwissTrainSpotting` or similar is explicitly applied with `EdgeToEdge` support.

### New Files (Create)
1.  **`app/src/main/res/layout/activity_image_classification.xml`**
2.  **`app/src/main/java/com/tb/swisstrainspotting/ImageClassificationActivity.java`**
3.  **`app/src/main/AndroidManifest.xml`**
    - *Action*: Register the new `ImageClassificationActivity` explicitly.
4.  **`app/src/main/res/raw/demo_image_placeholder.jpg` (or png)**
    - A simple, generic image resource to load into ImageView for testing purposes.

## 5. Step-by-step Implementation Workflow

1.  **Set up Placeholder Resource**: Place a small `placeholder.png` in the `res/drawable` or `res/raw` folder so Java code has an asset to render.
2.  **Define Main Layout (`activity_main.xml`)**:
    - Add the Title TextView and two Buttons ("Image", "Camera"). Use `layout_margin` (dp units) for breathing room between views.
3.  **Define Classifier Layout (`activity_image_classification.xml`)**:
    - Add a standard Toolbar or just use the default ActionBar back-arrow. Add an ImageView filling the remaining space below the header bar.
4.  **Create ImageClassificationActivity.java**: 
    - Standard empty activity boilerplate (extends `AppCompatActivity`). 
    - In `onCreate`, inflate layout and set the placeholder drawable on the ImageView.
5.  **Wire up MainActivity.java**: 
    - Find buttons by ID using `findViewById` (or View binding if enabled, but raw IDs are simpler for this specific scope).
    - Add `button.setOnClickListener(v -> startActivity(...))` logic pointing to `ImageClassificationActivity.class`.
6.  **Manifest Configuration**: Ensure `manifests/AndroidManifest.xml` includes `<activity android:name=".ImageClassificationActivity" />`.
7.  **Run & Verify**: Launch simulator/device. Ensure clicking either button reliably transitions the UI and that the back button works.

## 6. Testing Strategy

-   **Visual Check**: Validate the title is clearly visible and buttons are equally sized on both Portrait (Phone) and Landscape/Tablet modes.
-   **Navigation Flow**: 
    - Click "Image" → Verify layout change and placeholder image appears.
    - Press physical/system Back → Return to main menu.
    - Click "Camera" → Verify layout change and placeholder image appears (do not test actual camera yet).
-   **System Bar Handling**: Check that the UI does not look "squashed" at the top/bottom on modern screens (handling status/navigation bars).

## 7. Risks / Likely Mistakes
1.  **Missing Manifest Registration**: Forgetting to add `<activity>` in `AndroidManifest.xml` will cause a fatal crash when clicking a button. 
2.  **Ignoring `dp` vs `px`**: Hardcoding width/height in `px` in XML layouts causes UI distortion across different pixel densities (mdpi vs xhdpi).
3.  **Main Thread Freezing**: While unlikely with just two buttons, ensuring the activity transition isn't blocked by unnecessary initialization logic is crucial.
4.  **Orientation Changes**: Without saving instance state, simple data passing will be lost. However, since Module 1 uses hardcoded placeholders, this specific risk is minimized.

## 8. Suggested AI Tool Usage for this module
-   `list_files`: Verify existing drawable resources or check the current XML skeleton.
-   `write_file` / `render_compose_preview` (for visual layout preview assistance): Assist in generating exact ConstraintLayout code or Java stubs for new activities.
-   `analyze_file`: Check `AndroidManifest.xml` and `MainActivity.java` after writing to ensure there are no syntax errors or unresolved ID references before compiling.

## 9. Example Prompts for Implementation

1.  *"Generate XML layout for a simple vertical LinearLayout containing a centered Heading 'SwissTrainSpotting' and two symmetric buttons below it labeled 'Image' and 'Camera'. Use standard material design attributes."*

2.  *"Create the Java boilerplate for a new Android Activity named 'ImageClassificationActivity' in package 'com.tb.swisstrainspotting'. It should extend AppCompatActivity, inflate 'activity_image_classification.xml', and set an ImageView inside it to display `R.drawable.placeholder_logo`."*

3.  *"Update my MainActivity.java to add click listeners to the buttons with IDs 'btnImage' and 'btnCamera' so they launch 'ImageClassificationActivity' using standard Android Intents."*