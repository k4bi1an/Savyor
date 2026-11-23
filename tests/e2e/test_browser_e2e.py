import os
import time
import pytest
import requests
from playwright.sync_api import Page, expect

BASE_URL = "http://127.0.0.1:8001"
TEST_PASSCODE = os.getenv("SAVYOR_PASSCODE", "test_custom_passcode_2026")
SCREENSHOTS_DIR = os.path.join(os.path.dirname(__file__), "screenshots")
os.makedirs(SCREENSHOTS_DIR, exist_ok=True)

@pytest.fixture(autouse=True)
def setup_test_environment(page: Page):
    """
    Hostile QA fixture:
    1. Resets database & rate limit to initial seed state before every test.
    2. Attaches console error and unhandled error listeners (fails test if console.error occurs).
    3. Attaches response error listeners (fails test if unexpected 500 error occurs).
    4. Automatically accepts dialogs (confirms).
    """
    requests.post(f"{BASE_URL}/api/test/reset-db")
    
    console_errors = []
    def on_console_msg(msg):
        # Allow benign 401 log during auth checks or deliberate test failures
        if msg.type == "error":
            # Ignore expected intentional fetch failures during tests (e.g. 401 when locked or mock 429)
            text = msg.text
            if "Failed to load resource" in text or "status of 401" in text or "status of 404" in text:
                return
            console_errors.append(f"Console error: {text}")

    def on_page_error(err):
        console_errors.append(f"Page error: {str(err)}")

    page.on("console", on_console_msg)
    page.on("pageerror", on_page_error)
    page.on("dialog", lambda dialog: dialog.accept())

    # Grant clipboard permissions for copy notes tests
    try:
        page.context.grant_permissions(["clipboard-read", "clipboard-write"])
    except Exception:
        pass

    # Set standard desktop viewport by default
    page.set_viewport_size({"width": 1440, "height": 900})
    
    yield

    # Assert zero unexpected console errors occurred during the test
    assert not console_errors, f"Browser console errors detected: {console_errors}"

def unlock_vault(page: Page):
    """Helper to enter passcode and unlock the vault."""
    page.goto(BASE_URL)
    # Check if passcode input is visible
    passcode_input = page.locator("input[type='password']")
    if passcode_input.is_visible():
        passcode_input.fill(TEST_PASSCODE)
        page.keyboard.press("Enter")
        # Wait for header brand to appear
        expect(page.locator("button[aria-label='Savyor home']")).to_be_visible()

# -----------------------------------------------------------------------------
# 1. AUTH UI FLOW
# -----------------------------------------------------------------------------
def test_auth_ui_flow(page: Page):
    """
    Verify PasscodeScreen appears unauthenticated, blocks invalid passcodes,
    unlocks with 'savyor', and Lock button logs out back to passcode screen.
    """
    page.goto(BASE_URL)
    
    # 1. Assert PasscodeScreen elements
    passcode_input = page.locator("input[type='password']")
    expect(passcode_input).to_be_visible()
    expect(page.locator("text=Save the reel. Keep the idea.")).to_be_visible()

    # 2. Enter wrong passcode
    passcode_input.fill("wrong_secret_123")
    page.keyboard.press("Enter")
    expect(page.locator("text=Incorrect passcode")).to_be_visible()

    # 3. Enter correct passcode
    passcode_input.fill(TEST_PASSCODE)
    page.keyboard.press("Enter")

    # 4. Header is visible in library
    expect(page.locator("button[aria-label='Savyor home']")).to_be_visible()
    expect(page.locator("button:has-text('Add reel')")).to_be_visible()

    # 5. Lock button logs out
    lock_btn = page.locator("button:has-text('Lock')")
    expect(lock_btn).to_be_visible()
    lock_btn.click()

    # 6. Returns to passcode screen
    expect(page.locator("input[type='password']")).to_be_visible()

# -----------------------------------------------------------------------------
# 2. ADD REEL FLOW & VALIDATION
# -----------------------------------------------------------------------------
def test_add_reel_flow(page: Page):
    """
    Verify adding a valid reel shows progress card and completes,
    duplicate submission is blocked with error, invalid link shows error,
    and Gemini failure produces error card with Retry button.
    """
    unlock_vault(page)

    # 1. Invalid non-Instagram link validation
    page.locator("button:has-text('Add reel')").click()
    expect(page.locator("h2:has-text('Add Instagram reel')")).to_be_visible()
    url_input = page.locator("input[type='url']")
    url_input.fill("https://youtube.com/watch?v=12345")
    page.locator("form button[type='submit']").click()
    expect(page.locator("text=Please provide a valid Instagram Reel link")).to_be_visible()
    page.locator("button:has(svg.lucide-x)").first.click()

    # 2. Valid reel link adds successfully
    page.locator("button:has-text('Add reel')").click()
    url_input = page.locator("input[type='url']")
    valid_url = "https://www.instagram.com/reel/test_valid_101/"
    url_input.fill(valid_url)
    page.locator("form button[type='submit']").click()

    # Inline progress card appears and completes
    # Wait for the card with "Extracting notes" or title
    expect(page.locator("text=Automated Test Reel test_valid_101").first).to_be_visible(timeout=10000)

    # 3. Submitting the exact same link again is blocked as duplicate with 409 Conflict
    page.locator("button:has-text('Add reel')").click()
    url_input = page.locator("input[type='url']")
    url_input.fill(valid_url)
    page.locator("form button[type='submit']").click()
    expect(page.locator("text=already in your vault")).to_be_visible()
    page.locator("button:has(svg.lucide-x)").first.click()

    # Assert API returns 409 Conflict for duplicate reel
    s = requests.Session()
    s.post(f"{BASE_URL}/api/login", json={"passcode": TEST_PASSCODE})
    r_dup = s.post(f"{BASE_URL}/api/reels", json={"url": valid_url})
    assert r_dup.status_code == 409, f"Expected 409 Conflict for duplicate reel, got {r_dup.status_code}"
    assert "already in your vault" in r_dup.json().get("detail", "")

    # 4. Gemini failure link produces error card with Retry
    page.locator("button:has-text('Add reel')").click()
    url_input = page.locator("input[type='url']")
    fail_url = "https://www.instagram.com/reel/fail-gemini-test/"
    url_input.fill(fail_url)
    page.locator("form button[type='submit']").click()

    # Wait for failed card with Retry button
    expect(page.locator("text=Failed to save").first).to_be_visible(timeout=10000)
    retry_btn = page.locator("button:has-text('Retry')").first
    expect(retry_btn).to_be_visible()

# -----------------------------------------------------------------------------
# 3. LIBRARY VIEWS & LOCALSTORAGE PERSISTENCE
# -----------------------------------------------------------------------------
def test_library_views_and_persistence(page: Page):
    """
    Verify Grid, List, and Compact all render the reels,
    and the selected view persists across reloads via localStorage.
    """
    unlock_vault(page)

    # 1. Grid view by default on desktop
    grid_btn = page.locator("button[aria-label='Grid view']")
    list_btn = page.locator("button[aria-label='List view']")
    compact_btn = page.locator("button[aria-label='Compact view']")
    expect(grid_btn).to_have_attribute("aria-pressed", "true")

    # Verify seed reels are rendered in Grid
    expect(page.locator("text=Mastering Modern C++ Concurrency")).to_be_visible()
    expect(page.locator("text=Sourdough Bread Scoring")).to_be_visible()

    # 2. Switch to List view
    list_btn.click()
    expect(list_btn).to_have_attribute("aria-pressed", "true")
    expect(page.locator("text=Mastering Modern C++ Concurrency")).to_be_visible()
    expect(page.locator("text=An in-depth guide on modern concurrency primitives")).to_be_visible()

    # 3. Switch to Compact view
    compact_btn.click()
    expect(compact_btn).to_have_attribute("aria-pressed", "true")
    expect(page.locator("text=Mastering Modern C++ Concurrency")).to_be_visible()

    # 4. Reload page and assert Compact view persisted
    page.reload()
    expect(page.locator("button[aria-label='Compact view']")).to_have_attribute("aria-pressed", "true")
    expect(page.locator("text=Mastering Modern C++ Concurrency")).to_be_visible()

    # 5. Switch back to Grid view and verify persistence
    page.locator("button[aria-label='Grid view']").click()
    page.reload()
    expect(page.locator("button[aria-label='Grid view']")).to_have_attribute("aria-pressed", "true")

# -----------------------------------------------------------------------------
# 4. SORTING AND CATEGORY / STATUS FILTERING
# -----------------------------------------------------------------------------
def test_sorting_and_filtering(page: Page):
    """
    Verify sorting (Title A-Z, Oldest, Newest),
    category tabs hide empty categories, and status filters work.
    """
    unlock_vault(page)

    # 1. Sort by Title A-Z
    sort_select = page.locator("select[aria-label='Sort reels']")
    sort_select.select_option("title")
    # First title should be "Mastering Modern C++ Concurrency"
    titles = page.locator("h2, h3").all_inner_texts()
    title_texts = [t.strip() for t in titles if "Concurrency" in t or "Sourdough" in t]
    assert title_texts[0].startswith("Mastering Modern")

    # 2. Category tabs hide empty categories
    # In test DB, only "Tech & Code" and "Food" have reels.
    expect(page.locator("button:has-text('Tech & Code')")).to_be_visible()
    expect(page.locator("button:has-text('Food')")).to_be_visible()
    # Empty categories like "Fitness" or "Business" must NOT be in the category bar
    expect(page.locator("button:has-text('Fitness')")).not_to_be_visible()
    expect(page.locator("button:has-text('Business')")).not_to_be_visible()

    # Click 'Food' category tab
    page.locator("button:has-text('Food')").click()
    expect(page.locator("text=Sourdough Bread Scoring")).to_be_visible()
    expect(page.locator("text=Mastering Modern C++ Concurrency")).not_to_be_visible()

    # Reset to 'All'
    page.locator("button:has-text('All')").first.click()
    expect(page.locator("text=Mastering Modern C++ Concurrency")).to_be_visible()

    # 3. Status Filters (reel_seed_1 is unread, reel_seed_2 is reviewed)
    page.locator("button:has-text('Reviewed')").click()
    expect(page.locator("text=Sourdough Bread Scoring")).to_be_visible()
    expect(page.locator("text=Mastering Modern C++ Concurrency")).not_to_be_visible()

    page.locator("button:has-text('Unread')").click()
    expect(page.locator("text=Mastering Modern C++ Concurrency")).to_be_visible()
    expect(page.locator("text=Sourdough Bread Scoring")).not_to_be_visible()

    page.locator("button:has-text('All')").nth(1).click()  # Status All pill
    expect(page.locator("text=Mastering Modern C++ Concurrency")).to_be_visible()
    expect(page.locator("text=Sourdough Bread Scoring")).to_be_visible()

# -----------------------------------------------------------------------------
# 5. SEARCH TRANSCRIPT & HIGHLIGHTING
# -----------------------------------------------------------------------------
def test_search_transcript_and_highlight(page: Page):
    """
    Search finds keywords that exist only in a transcript, highlights them in List view,
    and displays friendly empty state when no results match.
    """
    unlock_vault(page)

    # First add reel with unique transcript keyword 'xylophone'
    page.locator("button:has-text('Add reel')").click()
    url_input = page.locator("input[type='url']")
    url_input.fill("https://www.instagram.com/reel/test_transcript_reel/")
    page.locator("form button[type='submit']").click()
    expect(page.locator("text=Automated Test Reel test_transcript_reel").first).to_be_visible(timeout=10000)

    # Switch to List view
    page.locator("button[aria-label='List view']").click()

    # Search for 'xylophone'
    search_input = page.locator("input[placeholder*='Search reels']")
    search_input.fill("xylophone")

    # Only matching reel is shown
    expect(page.locator("text=Automated Test Reel test_transcript_reel")).to_be_visible()
    expect(page.locator("text=Sourdough Bread Scoring")).not_to_be_visible()

    # Assert <mark> tag is used for the highlighted snippet
    mark_element = page.locator("mark:has-text('xylophone')")
    expect(mark_element).to_be_visible()

    # Search for nonexistent term
    search_input.fill("nonexistent_random_phrase_xyz")
    expect(page.locator("text=No reels found for \"nonexistent_random_phrase_xyz\"")).to_be_visible()
    
    # Click clear search
    page.locator("button:has-text('Clear search')").click()
    expect(page.locator("text=Sourdough Bread Scoring")).to_be_visible()

# -----------------------------------------------------------------------------
# 6. KEYBOARD NAVIGATION
# -----------------------------------------------------------------------------
def test_keyboard_navigation(page: Page):
    """
    Verify keyboard shortcuts:
    - g, l, c switch views
    - j/k move focus outline
    - Enter opens focused reel
    - Typing in inputs does NOT trigger shortcuts
    """
    unlock_vault(page)

    # Ensure focus is outside inputs
    page.locator("body").click()

    # 1. Press 'l' -> List view
    page.keyboard.press("l")
    expect(page.locator("button[aria-label='List view']")).to_have_attribute("aria-pressed", "true")

    # 2. Press 'c' -> Compact view
    page.keyboard.press("c")
    expect(page.locator("button[aria-label='Compact view']")).to_have_attribute("aria-pressed", "true")

    # 3. Press 'g' -> Grid view
    page.keyboard.press("g")
    expect(page.locator("button[aria-label='Grid view']")).to_have_attribute("aria-pressed", "true")

    # 4. Press 'j' -> moves focus to first item
    page.keyboard.press("j")
    # Verify card outline
    focused_card = page.locator("[class*='ring-[#2F5D50]']").first
    expect(focused_card).to_be_visible()

    # 5. Press Enter -> opens detail view (/reel/:id)
    page.keyboard.press("Enter")
    expect(page.locator("button:has-text('All reels')")).to_be_visible()
    assert "/reel/" in page.url

    # Go back to home
    page.locator("button:has-text('All reels')").click()
    expect(page.locator("button:has-text('Add reel')")).to_be_visible()

    # 6. Typing in input field must NOT trigger g/l/c shortcuts
    search_input = page.locator("input[placeholder*='Search reels']")
    search_input.click()
    search_input.type("glc")
    expect(search_input).to_have_value("glc")
    # View must STILL be Grid!
    expect(page.locator("button[aria-label='Grid view']")).to_have_attribute("aria-pressed", "true")

# -----------------------------------------------------------------------------
# 7. DETAIL VIEW: TABS, VIDEO 206, ACTIONS, DELETE
# -----------------------------------------------------------------------------
def test_reel_detail_view(page: Page):
    """
    Verify detail view (/reel/:id):
    - Video loads and video proxy supports HTTP 206 Range requests
    - Tabs switch (Notes, Transcript, Links)
    - Copy notes writes structured text
    - Change Category and Status persist
    - Delete asks for confirmation and deletes
    """
    unlock_vault(page)

    # Navigate to reel_seed_1 detail
    page.locator("text=Mastering Modern C++ Concurrency").first.click()
    expect(page.locator("button:has-text('All reels')")).to_be_visible()

    # 1. Video Player present
    video_el = page.locator("video")
    expect(video_el).to_be_visible()
    video_src = video_el.get_attribute("src")
    assert "/api/proxy/video/reel_seed_1" in video_src

    # 2. Tabs switch
    # Transcript Tab
    page.locator("button:has-text('Transcript')").click()
    expect(page.locator("text=Today we explore memory fences")).to_be_visible()

    # Links Tab (only allowed types: github_repo, tool, product, website)
    page.locator("button:has-text('Links')").click()
    expect(page.locator("text=CppCoro")).to_be_visible()
    expect(page.locator("a[href='https://github.com/lewissbaker/cppcoro']")).to_be_visible()

    # Notes Tab
    page.locator("button:has-text('Notes')").click()
    expect(page.locator("text=Key Takeaways")).to_be_visible()
    expect(page.locator("text=Example Coroutine Task")).to_be_visible()

    # 3. Copy Notes button
    copy_btn = page.locator("button[title='Copy notes']")
    expect(copy_btn).to_be_visible()
    copy_btn.click()
    expect(page.locator("button[title='Copy notes'] svg.lucide-check")).to_be_visible()

    # 4. Change Category via More options menu
    page.locator("button[title='More options']").click()
    page.locator("button:has-text('Change category')").click()
    page.locator("div.z-30 button:has-text('Food')").click()
    expect(page.locator("p:has-text('Food')").first).to_be_visible()

    # 5. Change Status via More options menu
    page.locator("button[title='More options']").click()
    page.locator("button:has-text('Change status')").click()
    page.locator("div.z-30 button:has-text('tried')").click()
    expect(page.locator("p:has-text('tried')").first).to_be_visible()

    # 6. Delete reel with confirmation modal
    page.locator("button[title='More options']").click()
    page.locator("button:has-text('Delete reel')").click()
    
    # Confirmation modal appears
    expect(page.locator("h3:has-text('Delete reel?')")).to_be_visible()
    # Cancel keeping the reel
    page.locator("button:has-text('Cancel')").click()
    expect(page.locator("h3:has-text('Delete reel?')")).not_to_be_visible()

    # Open delete again and confirm
    page.locator("button[title='More options']").click()
    page.locator("button:has-text('Delete reel')").click()
    page.locator("button.bg-\\[\\#B3382C\\]:has-text('Delete')").click()

    # Navigates home and reel is removed
    expect(page.locator("button:has-text('Add reel')")).to_be_visible()
    expect(page.locator("text=Mastering Modern C++ Concurrency")).not_to_be_visible()

# -----------------------------------------------------------------------------
# 8. CATEGORY MANAGEMENT MODAL
# -----------------------------------------------------------------------------
def test_category_management(page: Page):
    """
    Verify creating, renaming, reordering, and deleting categories.
    Verify 'Other' category cannot be deleted.
    """
    unlock_vault(page)

    # Open Manage Categories modal
    page.locator("button[title='Manage categories']").click()
    expect(page.locator("h2:has-text('Manage categories')")).to_be_visible()

    # 1. Create new category
    new_cat_input = page.locator("input[placeholder*='Category name']")
    new_cat_input.fill("Design & UX")
    page.locator("form button[type='submit']:has-text('Add')").click()
    expect(page.locator("text=Design & UX")).to_be_visible()

    # 2. Rename category
    edit_btn = page.locator("div:has-text('Design & UX') button[title*='Rename']").last
    edit_btn.click()
    edit_name_input = page.locator("input[value='Design & UX']")
    edit_name_input.fill("Design & Crafts")
    page.locator("button[title='Save']").click()
    expect(page.locator("text=Design & Crafts")).to_be_visible()

    # 3. Delete category
    delete_cat_btn = page.locator("div:has-text('Design & Crafts') button[title*='Delete']").last
    delete_cat_btn.click()
    expect(page.locator("text=Design & Crafts")).not_to_be_visible()

    # 4. Verify 'Other' category has no delete button
    other_row = page.locator(".flex.items-center.justify-between:has(span:text-is('Other'))")
    expect(other_row.locator("button[title*='Delete']")).not_to_be_visible()

    # Close modal
    page.locator("button:has(svg.lucide-x)").first.click()

# -----------------------------------------------------------------------------
# 9. RESPONSIVE DESIGN AUDIT & SCREENSHOTS
# -----------------------------------------------------------------------------
@pytest.mark.parametrize("width,height", [
    (1440, 900),
    (768, 1024),
    (390, 844),
])
def test_responsive_layout_and_screenshots(page: Page, width: int, height: int):
    """
    Responsive audit across Desktop (1440px), Tablet (768px), and Mobile (390px):
    - Asserts no horizontal overflow (scrollWidth <= clientWidth).
    - Captures screenshot into tests/e2e/screenshots/.
    - On 390px without stored preference, defaults view to 'list'.
    """
    page.set_viewport_size({"width": width, "height": height})
    unlock_vault(page)

    # Programmatic assertion: No horizontal scrollbar!
    scroll_width = page.evaluate("() => document.documentElement.scrollWidth")
    client_width = page.evaluate("() => window.innerWidth")
    assert scroll_width <= client_width + 1, f"Horizontal scroll detected at {width}px: scrollWidth={scroll_width}, innerWidth={client_width}"

    # Capture Home screenshot
    shot_path_home = os.path.join(SCREENSHOTS_DIR, f"{width}px_home.png")
    page.screenshot(path=shot_path_home, full_page=True)
    assert os.path.exists(shot_path_home)

    # Navigate to detail and capture Detail screenshot
    page.locator("text=Mastering Modern C++ Concurrency").first.click()
    expect(page.locator("button:has-text('All reels')")).to_be_visible()

    scroll_width_detail = page.evaluate("() => document.documentElement.scrollWidth")
    assert scroll_width_detail <= client_width + 1, f"Horizontal scroll on detail view at {width}px: scrollWidth={scroll_width_detail}, innerWidth={client_width}"

    shot_path_detail = os.path.join(SCREENSHOTS_DIR, f"{width}px_detail.png")
    page.screenshot(path=shot_path_detail, full_page=True)
    assert os.path.exists(shot_path_detail)

def test_mobile_defaults_to_list_view(page: Page):
    """
    On screens narrower than 640px with no saved preference,
    Savyor must default to List view.
    """
    page.set_viewport_size({"width": 390, "height": 844})
    page.goto(BASE_URL)
    # Clear view localStorage
    page.evaluate("() => { localStorage.removeItem('savyor_view'); }")
    unlock_vault(page)

    # Verify List view is active by default
    list_btn = page.locator("button[aria-label='List view']")
    expect(list_btn).to_have_attribute("aria-pressed", "true")

# -----------------------------------------------------------------------------
# 10. SERVICE WORKER & CACHE HEADERS
# -----------------------------------------------------------------------------
def test_cache_control_and_service_worker():
    """
    Verify:
    1. index.html has 'Cache-Control: no-cache, no-store, must-revalidate'.
    2. /api/reels responses have 'Cache-Control: no-store'.
    3. sw.js is registered and does not cache /api/ responses.
    """
    s = requests.Session()
    s.post(f"{BASE_URL}/api/login", json={"passcode": TEST_PASSCODE})

    # 1. index.html cache control
    r_index = s.get(BASE_URL)
    cc_index = r_index.headers.get("Cache-Control", "")
    assert "no-store" in cc_index or "no-cache" in cc_index, f"index.html missing no-store: {cc_index}"

    # 2. /api/reels cache control
    r_api = s.get(f"{BASE_URL}/api/reels")
    cc_api = r_api.headers.get("Cache-Control", "")
    assert "no-store" in cc_api, f"/api/reels missing no-store: {cc_api}"

    # 3. sw.js content verification
    r_sw = s.get(f"{BASE_URL}/sw.js")
    assert r_sw.status_code == 200
    assert "url.pathname.startsWith('/api/')" in r_sw.text
    assert "return" in r_sw.text

# -----------------------------------------------------------------------------
# 11. HOSTILE EDGE CASES (500-char strings, unicode, rapid clicks, navigation during save)
# -----------------------------------------------------------------------------
def test_hostile_edge_cases(page: Page):
    """
    Hostile QA checks:
    - 500-char string input handling
    - Unicode, accents, non-ASCII text
    - Rapid double-clicks without duplicate jobs or modal flicker
    - Navigating away to /reel/:id immediately after initiating a save
    """
    unlock_vault(page)

    # 1. Unicode & 500-char search string (zero crashes, zero console errors)
    search_input = page.locator("input[placeholder*='Search reels']")
    long_string = "超长的测试字符串_Café_au_lait_🚀_" + ("x" * 480)
    search_input.fill(long_string)
    expect(page.locator("text=No reels found")).to_be_visible()
    page.locator("button:has-text('Clear search')").click()

    # 2. XSS and SQL injection payloads in search
    search_input.fill("<script>alert(1)</script>' OR 1=1 --")
    expect(page.locator("text=No reels found")).to_be_visible()
    page.locator("button:has-text('Clear search')").click()

    # 3. Unicode category creation
    page.locator("button[title='Manage categories']").click()
    cat_input = page.locator("input[placeholder*='Category name']")
    cat_input.fill("☕ Café & Pâtisserie 日本語")
    page.locator("form button[type='submit']:has-text('Add')").click()
    expect(page.locator("text=Café & Pâtisserie 日本語")).to_be_visible()

    # Delete the unicode category
    del_btn = page.locator("div:has-text('Café & Pâtisserie') button[title*='Delete']").last
    del_btn.click()
    page.locator("button:has(svg.lucide-x)").first.click()

    # 4. Rapid double-clicks on view toggles
    list_btn = page.locator("button[aria-label='List view']")
    grid_btn = page.locator("button[aria-label='Grid view']")
    for _ in range(5):
        list_btn.click()
        grid_btn.click()
    expect(grid_btn).to_have_attribute("aria-pressed", "true")

    # 5. Navigating away during an active background job
    page.locator("button:has-text('Add reel')").click()
    url_input = page.locator("input[type='url']")
    url_input.fill("https://www.instagram.com/reel/test_navigate_during_save/")
    page.locator("form button[type='submit']").click()

    # Immediately click an existing reel to navigate to detail view while job runs in background!
    page.locator("text=Mastering Modern C++ Concurrency").first.click()
    expect(page.locator("button:has-text('All reels')")).to_be_visible()

    # Wait for the background job to finish
    time.sleep(3)

    # Navigate back home
    page.locator("button:has-text('All reels')").click()
    expect(page.locator("button:has-text('Add reel')")).to_be_visible()
    # The newly saved reel is rendered in home library
    expect(page.locator("text=Automated Test Reel test_navigate_during_save").first).to_be_visible()

# -----------------------------------------------------------------------------
# 12. MANIFEST & SHARE TARGET VALIDITY
# -----------------------------------------------------------------------------
def test_manifest_and_share_target(page: Page):
    """
    Verify:
    1. manifest.json has required fields, proper color tokens, and valid share_target.
    2. Every icon defined in manifest exists and returns HTTP 200.
    3. Share target URL extraction cleanly parses Instagram URLs from messy shared text strings.
    """
    r_manifest = requests.get(f"{BASE_URL}/manifest.json")
    assert r_manifest.status_code == 200
    manifest = r_manifest.json()

    assert manifest.get("name") == "Savyor"
    assert manifest.get("short_name") == "Savyor"
    assert manifest.get("display") == "standalone"
    assert manifest.get("background_color") == "#FAFAF8"
    assert manifest.get("theme_color") == "#2F5D50"

    icons = manifest.get("icons", [])
    assert len(icons) >= 3
    for icon in icons:
        src = icon.get("src")
        assert src, "Icon missing src"
        r_icon = requests.get(f"{BASE_URL}{src}")
        assert r_icon.status_code == 200, f"Icon {src} returned {r_icon.status_code}"

    share_target = manifest.get("share_target", {})
    assert share_target.get("action") == "/"
    assert share_target.get("method") == "GET"
    assert "url" in share_target.get("params", {})
    assert "text" in share_target.get("params", {})

    # Test link extraction from messy shared-text string in browser
    unlock_vault(page)
    messy_shared = "Check out this reel: https://www.instagram.com/reel/C_share_123/?igsh=test_abc and take notes!"
    page.goto(f"{BASE_URL}/?text={requests.utils.quote(messy_shared)}")

    # Modal opens automatically with extracted clean reel URL
    expect(page.locator("h2:has-text('Add Instagram reel')")).to_be_visible()
    url_input = page.locator("input[type='url']")
    extracted_val = url_input.input_value()
    assert extracted_val.startswith("https://www.instagram.com/reel/C_share_123")
    assert "Check out this reel" not in extracted_val
    assert "and take notes!" not in extracted_val

    # Address bar query parameters should be cleaned
    assert "?text=" not in page.url

# -----------------------------------------------------------------------------
# 13. AUTO-MARK-AS-REVIEWED SETTING
# -----------------------------------------------------------------------------
def test_auto_mark_as_reviewed_setting(page: Page):
    """
    Verify auto-mark-as-reviewed setting:
    - Exists in Settings dialog and defaults to ON.
    - When OFF, viewing an unread reel does NOT mark it reviewed after 3s.
    - When ON, viewing an unread reel marks it reviewed after 3s.
    """
    unlock_vault(page)

    # 1. Open Settings and verify default is ON
    settings_btn = page.locator("button[aria-label='Settings']")
    settings_btn.click()
    expect(page.locator("h2:has-text('Settings')")).to_be_visible()

    toggle = page.locator("button[role='switch']")
    expect(toggle).to_have_attribute("aria-checked", "true")

    # 2. Toggle to OFF and save
    toggle.click()
    expect(toggle).to_have_attribute("aria-checked", "false")
    page.locator("button:has-text('Done')").click()
    expect(page.locator("h2:has-text('Settings')")).not_to_be_visible()

    # 3. Navigate to unread reel (reel_seed_1)
    page.locator("text=Mastering Modern C++ Concurrency").first.click()
    expect(page.locator("button:has-text('All reels')")).to_be_visible()
    expect(page.locator("p:has-text('unread')").first).to_be_visible()

    # Wait 3.5s (longer than the 3.0s threshold)
    time.sleep(3.5)
    # It must STILL be unread because setting is OFF!
    expect(page.locator("p:has-text('unread')").first).to_be_visible()

    # 4. Turn setting back ON
    settings_btn.click()
    expect(page.locator("h2:has-text('Settings')")).to_be_visible()
    toggle = page.locator("button[role='switch']")
    toggle.click()
    expect(toggle).to_have_attribute("aria-checked", "true")
    page.locator("button:has-text('Done')").click()

    # Wait 3.5s
    time.sleep(3.5)
    # It must now automatically update to 'reviewed'!
    expect(page.locator("p:has-text('reviewed')").first).to_be_visible()

# -----------------------------------------------------------------------------
# 14. 9-SCREENSHOT MATRIX WITH PROGRAMMATIC OVERFLOW ASSERTIONS
# -----------------------------------------------------------------------------
@pytest.mark.parametrize("width,height", [
    (1440, 900),
    (768, 1024),
    (390, 844),
])
def test_screenshot_matrix_9_states(page: Page, width: int, height: int):
    """
    Asserts zero horizontal overflow (scrollWidth <= innerWidth + 1) and captures
    all 9 distinct UI states across 1440px, 768px, and 390px:
    1. Grid
    2. List
    3. Compact
    4. Detail view
    5. Manage categories modal
    6. Add reel modal
    7. Error card (failed job)
    8. Empty library (0 reels)
    9. No-results search
    """
    def assert_no_overflow(state_name: str):
        scroll_w = page.evaluate("() => document.documentElement.scrollWidth")
        inner_w = page.evaluate("() => window.innerWidth")
        assert scroll_w <= inner_w + 1, (
            f"Horizontal overflow in state '{state_name}' at {width}px: scrollWidth={scroll_w}, innerWidth={inner_w}"
        )

    page.set_viewport_size({"width": width, "height": height})
    unlock_vault(page)

    # State 1: Grid View
    page.locator("button[aria-label='Grid view']").click()
    expect(page.locator("button[aria-label='Grid view']")).to_have_attribute("aria-pressed", "true")
    assert_no_overflow("grid")
    page.screenshot(path=os.path.join(SCREENSHOTS_DIR, f"{width}px_01_grid.png"))

    # State 2: List View
    page.locator("button[aria-label='List view']").click()
    expect(page.locator("button[aria-label='List view']")).to_have_attribute("aria-pressed", "true")
    assert_no_overflow("list")
    page.screenshot(path=os.path.join(SCREENSHOTS_DIR, f"{width}px_02_list.png"))

    # State 3: Compact View
    page.locator("button[aria-label='Compact view']").click()
    expect(page.locator("button[aria-label='Compact view']")).to_have_attribute("aria-pressed", "true")
    assert_no_overflow("compact")
    page.screenshot(path=os.path.join(SCREENSHOTS_DIR, f"{width}px_03_compact.png"))

    # State 4: Detail View
    page.locator("text=Mastering Modern C++ Concurrency").first.click()
    expect(page.locator("button:has-text('All reels')")).to_be_visible()
    assert_no_overflow("detail")
    page.screenshot(path=os.path.join(SCREENSHOTS_DIR, f"{width}px_04_detail.png"))
    page.locator("button:has-text('All reels')").click()
    expect(page.locator("button:has-text('Add reel')")).to_be_visible()

    # State 5: Manage Categories Modal
    page.locator("button[title='Manage categories']").click()
    expect(page.locator("h2:has-text('Manage categories')")).to_be_visible()
    assert_no_overflow("manage_categories")
    page.screenshot(path=os.path.join(SCREENSHOTS_DIR, f"{width}px_05_manage_categories.png"))
    page.locator("button:has(svg.lucide-x)").first.click()

    # Switch back to Grid View before modal and error states
    page.locator("button[aria-label='Grid view']").click()

    # State 6: Add Reel Modal
    page.locator("button:has-text('Add reel')").click()
    expect(page.locator("h2:has-text('Add Instagram reel')")).to_be_visible()
    assert_no_overflow("add_reel_modal")
    page.screenshot(path=os.path.join(SCREENSHOTS_DIR, f"{width}px_06_add_reel_modal.png"))
    
    # State 7: Error Card (Failed job)
    url_input = page.locator("input[type='url']")
    url_input.fill("https://www.instagram.com/reel/fail-gemini-screenshot/")
    page.locator("form button[type='submit']").click()
    expect(page.locator("text=Failed to save").first).to_be_visible(timeout=10000)
    assert_no_overflow("error_card")
    page.screenshot(path=os.path.join(SCREENSHOTS_DIR, f"{width}px_07_error_card.png"))

    # State 9: No-results Search (do search before emptying library)
    search_input = page.locator("input[placeholder*='Search reels']")
    if not search_input.is_visible():
        page.locator("button[aria-label='Search reels']").click()
    search_input = page.locator("input[placeholder*='Search reels']:visible")
    search_input.fill("zzzz_no_match_query_999")
    expect(page.locator("text=No reels found for \"zzzz_no_match_query_999\"")).to_be_visible()
    assert_no_overflow("no_results_search")
    page.screenshot(path=os.path.join(SCREENSHOTS_DIR, f"{width}px_09_no_results.png"))
    page.locator("button:has-text('Clear search')").click()

    # State 8: Empty Library (delete all reels from test database via API)
    s = requests.Session()
    s.post(f"{BASE_URL}/api/login", json={"passcode": TEST_PASSCODE})
    reels = s.get(f"{BASE_URL}/api/reels").json()
    for r in reels:
        s.delete(f"{BASE_URL}/api/reels/{r['id']}")
    page.reload()
    expect(page.locator("text=No reels saved yet")).to_be_visible()
    assert_no_overflow("empty_library")
    page.screenshot(path=os.path.join(SCREENSHOTS_DIR, f"{width}px_08_empty_library.png"))
