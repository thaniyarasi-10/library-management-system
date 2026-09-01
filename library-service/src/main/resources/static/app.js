/**
 * ATHENAEUM LIBRARY SYSTEM - COMMERCIAL SAAS APPLICATION LOGIC
 * Production Single-Page Application Client
 */

// Application State
const state = {
  theme: localStorage.getItem('athenaeum_theme') || 'dark',
  authToken: localStorage.getItem('athenaeum_token') || '',
  baseUrl: 'http://localhost:8080',
  currentPage: 'dashboard',
  userRole: '',
  currentUser: null,
  
  // Data Caches
  books: [],
  booksPage: 0,
  booksSize: 10,
  booksTotalPages: 1,
  booksTotalElements: 0,
  bookSearchQuery: '',

  members: [],
  membersPage: 0,
  membersSize: 10,
  membersTotalPages: 1,
  membersTotalElements: 0,
  memberSearchQuery: '',

  currentFineMemberId: null,
  confirmCallback: null
};

/// DOM Content Loaded Handler
document.addEventListener('DOMContentLoaded', () => {
  initTheme();
  checkAuthSession();
});

// Listen for OAuth postMessage callbacks from popup
window.addEventListener('message', (event) => {
  if (event.data && event.data.type === 'ATHENAEUM_OAUTH_TOKEN' && event.data.token) {
    state.authToken = event.data.token;
    localStorage.setItem('athenaeum_token', event.data.token);
    showToast('Signed in with Google successfully', 'success');
    closeModal('googleTokenModal');
    checkAuthSession();
  }
});

/* -------------------------------------------------------------------------- */
/* 1. THEME & INITIALIZATION                                                 */
/* -------------------------------------------------------------------------- */
function initTheme() {
  document.documentElement.setAttribute('data-theme', state.theme);
}

function toggleTheme() {
  state.theme = state.theme === 'dark' ? 'light' : 'dark';
  document.documentElement.setAttribute('data-theme', state.theme);
  localStorage.setItem('athenaeum_theme', state.theme);
}

/* -------------------------------------------------------------------------- */
/* 2. AUTHENTICATION & ROUTE GUARD                                            */
/* -------------------------------------------------------------------------- */
function parseJwt(token) {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;const base64Url = token.split('.')[1];
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(atob(base64).split('').map(function(c) {
      return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
    }).join(''));
    return JSON.parse(jsonPayload);
  } catch (e) {
    return null;
  }
}

function updateUiByRole() {
  const role = state.userRole || 'USER';
  const isAdmin = role === 'ADMIN';

  // 1. Sidebar Brand Tag
  const brandTag = document.querySelector('.brand-tag');
  if (brandTag) {
    brandTag.textContent = isAdmin ? 'ADMIN PORTAL' : 'MEMBER HUB';
  }

  // 2. Sidebar navigation links
  const membersNav = document.querySelector('.nav-link[data-page="members"]');
  if (membersNav) {
    if (isAdmin) {
      membersNav.classList.remove('hidden');
    } else {
      membersNav.classList.add('hidden');
    }
  }

  // Update navigation text labels for role
  const booksNavSpan = document.querySelector('.nav-link[data-page="books"] span');
  if (booksNavSpan) booksNavSpan.textContent = isAdmin ? 'Books Catalog' : 'Browse Catalog';

  const borrowNavSpan = document.querySelector('.nav-link[data-page="borrow"] span');
  if (borrowNavSpan) borrowNavSpan.textContent = isAdmin ? 'Borrowed Books' : 'Borrow & Return';

  const finesNavSpan = document.querySelector('.nav-link[data-page="fines"] span');
  if (finesNavSpan) finesNavSpan.textContent = isAdmin ? 'Fine Management' : 'My Fines & Dues';

  const membershipNavSpan = document.querySelector('.nav-link[data-page="membership"] span');
  if (membershipNavSpan) membershipNavSpan.textContent = isAdmin ? 'Membership' : 'Digital Membership';

  // 3. Profile Footer & Header
  const userName = state.currentUser ? (state.currentUser.name || state.currentUser.email) : (isAdmin ? 'Library Staff' : 'Library Member');
  const userEmail = state.currentUser ? state.currentUser.email : '';
  const userRoleText = isAdmin ? 'Librarian (Admin)' : 'Member';
  const monogram = getMonogram(userName);

  const sidebarAvatar = document.getElementById('sidebarAvatar');
  const sidebarUserName = document.getElementById('sidebarUserName');
  const sidebarUserRole = document.getElementById('sidebarUserRole');
  const headerAvatar = document.getElementById('headerAvatar');
  const headerUserName = document.getElementById('headerUserName');
  const dropdownUserName = document.getElementById('dropdownUserName');
  const dropdownUserEmail = document.getElementById('dropdownUserEmail');

  if (sidebarAvatar) sidebarAvatar.textContent = monogram;
  if (sidebarUserName) sidebarUserName.textContent = userName;
  if (sidebarUserRole) sidebarUserRole.textContent = userRoleText;
  if (headerAvatar) headerAvatar.textContent = monogram;
  if (headerUserName) headerUserName.textContent = userName;
  if (dropdownUserName) dropdownUserName.textContent = userName;
  if (dropdownUserEmail) dropdownUserEmail.textContent = userEmail;

  // 4. Dashboard View Toggle
  const adminDash = document.getElementById('adminDashboardSection');
  const userDash = document.getElementById('userDashboardSection');
  if (adminDash && userDash) {
    if (isAdmin) {
      adminDash.classList.remove('hidden');
      userDash.classList.add('hidden');
    } else {
      adminDash.classList.add('hidden');
      userDash.classList.remove('hidden');
    }
  }

  // 5. Books Catalog Action Button
  const addBookBtn = document.getElementById('addBookBtn');
  if (addBookBtn) {
    if (isAdmin) {
      addBookBtn.classList.remove('hidden');
    } else {
      addBookBtn.classList.add('hidden');
    }
  }

  // 6. Borrow Page Toggle
  const adminBorrow = document.getElementById('adminBorrowSection');
  const userBorrow = document.getElementById('userBorrowSection');
  if (adminBorrow && userBorrow) {
    if (isAdmin) {
      adminBorrow.classList.remove('hidden');
      userBorrow.classList.add('hidden');
    } else {
      adminBorrow.classList.add('hidden');
      userBorrow.classList.remove('hidden');
    }
  }

  // 7. Fines Page Setup
  const fineMemberSelectContainer = document.getElementById('fineMemberSelectContainer');
  const finesPageTitle = document.getElementById('finesPageTitle');
  const finesPageSubtitle = document.getElementById('finesPageSubtitle');
  if (fineMemberSelectContainer) {
    if (isAdmin) {
      fineMemberSelectContainer.classList.remove('hidden');
      if (finesPageTitle) finesPageTitle.textContent = 'Member Fine Settlement';
      if (finesPageSubtitle) finesPageSubtitle.textContent = 'Lookup member fines, outstanding balance, and settle payments';
    } else {
      fineMemberSelectContainer.classList.add('hidden');
      if (finesPageTitle) finesPageTitle.textContent = 'My Library Fines & Dues';
      if (finesPageSubtitle) finesPageSubtitle.textContent = 'View your outstanding dues and settle payments online';
    }
  }
}

async function userBorrowBook(bookId, bookTitle) {
  if (!state.currentUser || !state.currentUser.id) {
    showToast('Failed to borrow book: user profile not loaded', 'error');
    return;
  }

  // 1. Verify Active Membership
  if (state.membershipStatus !== 'ACTIVE') {
    const resMem = await fetchApi('/memberships/me');
    if (resMem.ok && resMem.data && resMem.data.status === 'ACTIVE') {
      state.membershipStatus = 'ACTIVE';
    } else {
      showToast('Active membership required to borrow books! Please apply & sign agreement.', 'error');
      navigateTo('membership');
      return;
    }
  }

  const res = await fetchApi('/borrow', {
    method: 'POST',
    body: JSON.stringify({ userId: state.currentUser.id, bookId: bookId })
  });

  if (res.ok && res.data) {
    showToast(bookTitle ? `Successfully borrowed "${bookTitle}"!` : 'Book borrowed successfully!', 'success');
    closeDrawer('bookDetailsDrawer');
    if (state.currentPage === 'dashboard') loadDashboardMetrics();
    if (state.currentPage === 'borrow') populateBorrowDropdowns();
  } else {
    const errorMsg = res.data && res.data.message ? res.data.message : 'Error issuing borrow request';
    if (res.status === 403 || (errorMsg && errorMsg.toLowerCase().includes('membership'))) {
      showToast('Active membership required to borrow books. Please activate your membership.', 'error');
      navigateTo('membership');
    } else {
      showToast(errorMsg, 'error');
    }
  }
}

async function checkAuthSession() {
  const authScreen = document.getElementById('authScreen');
  const appShell = document.getElementById('appShell');

  // Check if token is present in URL search query (e.g., ?token=... or ?access_token=...)
  const urlParams = new URLSearchParams(window.location.search);
  const urlToken = urlParams.get('token') || urlParams.get('access_token');
  if (urlToken) {
    state.authToken = urlToken;
    localStorage.setItem('athenaeum_token', urlToken);
    window.history.replaceState({}, document.title, window.location.pathname);
    showToast('Signed in with Google successfully!', 'success');
  }

  if (state.authToken) {
    // Decode JWT token and get user role
    const payload = parseJwt(state.authToken);
    if (payload && payload.roles && payload.roles.length > 0) {
      const mainRole = payload.roles[0].replace('ROLE_', '').toUpperCase();
      state.userRole = mainRole;
    } else {
      state.userRole = 'USER';
    }

    const isAdmin = state.userRole === 'ADMIN';
    if (!isAdmin && state.currentPage === 'members') {
      state.currentPage = 'dashboard';
    }

    // Fetch user profile
    try {
      const profileRes = await fetchApi('/user/me');
      if (profileRes.ok && profileRes.data) {
        state.currentUser = profileRes.data;
      }
    } catch (e) {
      console.warn('Could not load /user/me profile', e);
    }

    // Update UI based on Role
    updateUiByRole();

    authScreen.classList.add('hidden');
    appShell.classList.remove('hidden');
    navigateTo(state.currentPage);
    loadInitialAppData();
  } else {
    authScreen.classList.remove('hidden');
    appShell.classList.add('hidden');
  }
}

function handleGoogleSignIn() {
  const alertBox = document.getElementById('authAlert');
  if (alertBox) alertBox.classList.add('hidden');

  showToast('Redirecting to Google Sign-In...', 'info');
  // Seamless top-level redirect eliminates COOP popup blockage and token pasting
  window.location.href = `${state.baseUrl}/oauth2/authorization/google`;
}

function handleManualOAuthTokenSubmit(e) {
  e.preventDefault();
  const input = document.getElementById('manualOAuthTokenInput').value.trim();
  let tokenVal = input;

  try {
    const parsed = JSON.parse(input);
    if (parsed.token) tokenVal = parsed.token;
  } catch (err) {
    const match = input.match(/"token"\s*:\s*"([^"]+)"/);
    if (match) tokenVal = match[1];
  }

  if (tokenVal) {
    state.authToken = tokenVal;
    localStorage.setItem('athenaeum_token', tokenVal);
    closeModal('googleTokenModal');
    showToast('Signed in successfully', 'success');
    checkAuthSession();
  } else {
    showToast('Invalid token payload', 'error');
  }
}

async function handleLoginSubmit(event) {
  event.preventDefault();
  const emailInput = document.getElementById('loginEmail');
  const passwordInput = document.getElementById('loginPassword');
  const alertBox = document.getElementById('authAlert');
  const submitBtn = document.getElementById('loginSubmitBtn');

  alertBox.classList.add('hidden');
  alertBox.textContent = '';

  const email = emailInput.value.trim();
  const password = passwordInput.value;

  if (!email || !password) {
    alertBox.textContent = 'Please enter both email and password.';
    alertBox.classList.remove('hidden');
    return;
  }

  // Set loading state
  submitBtn.disabled = true;
  submitBtn.querySelector('.btn-text').textContent = 'Signing in...';

  try {
    const res = await fetchApi('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password })
    });

    if (res.ok && res.data && res.data.token) {
      state.authToken = res.data.token;
      localStorage.setItem('athenaeum_token', res.data.token);
      showToast('Welcome to Athenaeum Library System', 'success');
      checkAuthSession();
    } else {
      alertBox.textContent = res.data && res.data.message ? res.data.message : 'Invalid credentials. Please verify your email and password.';
      alertBox.classList.remove('hidden');
    }
  } catch (err) {
    alertBox.textContent = 'Unable to connect to library server. Please try again.';
    alertBox.classList.remove('hidden');
  } finally {
    submitBtn.disabled = false;
    submitBtn.querySelector('.btn-text').textContent = 'Sign in';
  }
}

function handleSignOut() {
  state.authToken = '';
  state.userRole = '';
  state.currentUser = null;
  localStorage.removeItem('athenaeum_token');
  showToast('Signed out successfully', 'info');
  checkAuthSession();
}

function togglePasswordVisibility(inputId, btn) {
  const input = document.getElementById(inputId);
  if (input.type === 'password') {
    input.type = 'text';
    btn.style.opacity = '1';
  } else {
    input.type = 'password';
    btn.style.opacity = '0.6';
  }
}

/* -------------------------------------------------------------------------- */
/* 3. HTTP FETCH INTERCEPTOR                                                  */
/* -------------------------------------------------------------------------- */
async function fetchApi(path, options = {}) {
  const cleanBase = state.baseUrl.replace(/\/+$/, '');
  const url = path.startsWith('http') ? path : `${cleanBase}${path.startsWith('/') ? path : '/' + path}`;

  const headers = options.headers || {};
  if (!(options.body instanceof FormData) && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }

  if (state.authToken) {
    headers['Authorization'] = `Bearer ${state.authToken}`;
  }

  try {
    const response = await fetch(url, { ...options, headers });
    let data;
    const contentType = response.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
      data = await response.json();
    } else {
      data = await response.text();
    }

    if (response.status === 401) {
      // Only unauthenticated sessions (expired/invalid JWT) trigger sign out
      if (state.authToken && !path.includes('/auth/login')) {
        handleSignOut();
      }
    }

    return {
      ok: response.ok,
      status: response.status,
      data
    };
  } catch (error) {
    return {
      ok: false,
      status: 0,
      data: { message: error.message || 'Network connection failed' }
    };
  }
}

/* -------------------------------------------------------------------------- */
/* 4. NAVIGATION & PAGES                                                      */
/* -------------------------------------------------------------------------- */
function navigateTo(pageId) {
  const isAdmin = state.userRole === 'ADMIN';

  // Guard admin-only members page
  if (pageId === 'members' && !isAdmin) {
    pageId = 'dashboard';
  }

  state.currentPage = pageId;

  // Highlight Nav Links
  document.querySelectorAll('.nav-link').forEach(link => {
    if (link.getAttribute('data-page') === pageId) {
      link.classList.add('active');
    } else {
      link.classList.remove('active');
    }
  });

  // Toggle Global Search (Hidden on Dashboard)
  const globalSearchContainer = document.getElementById('globalSearchContainer');
  if (globalSearchContainer) {
    if (pageId === 'dashboard') {
      globalSearchContainer.classList.add('hidden');
    } else {
      globalSearchContainer.classList.remove('hidden');
    }
  }

  // Switch Panes
  document.querySelectorAll('.page-pane').forEach(pane => {
    if (pane.id === `page-${pageId}`) {
      pane.classList.add('active');
    } else {
      pane.classList.remove('active');
    }
  });

  // Update Header Titles
  const titles = {
    dashboard: { 
      title: isAdmin ? 'Dashboard' : (state.currentUser ? `Welcome, ${state.currentUser.name || 'Member'}` : 'My Library Hub'), 
      sub: isAdmin ? 'Overview of library holdings and staff operations' : 'Explore catalog, track your reading and active loans' 
    },
    books: { 
      title: isAdmin ? 'Book Catalog' : 'Browse Catalog', 
      sub: isAdmin ? 'Manage library inventory, metadata, and book covers' : 'Explore our collection and check out titles' 
    },
    members: { 
      title: 'Library Members', 
      sub: 'View registered member profiles and patron records' 
    },
    borrow: { 
      title: 'Borrowed Books', 
      sub: isAdmin ? 'View all active and returned patron loans across the library' : 'Track your borrowed books, active loans, and due dates' 
    },
    fines: { 
      title: isAdmin ? 'Fine Management' : 'My Fines & Dues', 
      sub: isAdmin ? 'Lookup member fine balances and process payments' : 'Review your outstanding dues and settle payments' 
    },
    membership: {
      title: isAdmin ? 'Membership Accounts' : 'Digital Membership',
      sub: isAdmin ? 'Review patron membership agreements and statuses' : 'Apply, sign agreement, and access digital membership card'
    }
  };

  const info = titles[pageId] || { title: 'Athenaeum LMS', sub: 'Portal' };
  const titleDisplay = document.getElementById('pageTitleDisplay');
  const subDisplay = document.getElementById('pageSubtitleDisplay');
  if (titleDisplay) titleDisplay.textContent = info.title;
  if (subDisplay) subDisplay.textContent = info.sub;

  // Refresh page data
  if (pageId === 'dashboard') loadDashboardMetrics();
  if (pageId === 'books') loadBooks();
  if (pageId === 'members' && isAdmin) loadMembers();
  if (pageId === 'membership') loadMembershipStatus();
  if (pageId === 'borrow') {
    loadBorrows();
    populateBorrowDropdowns();
  }
  if (pageId === 'fines') {
    const selectContainer = document.getElementById('fineMemberSelectContainer');
    const memberColHeader = document.getElementById('fineMemberColHeader');
    if (!isAdmin) {
      if (selectContainer) selectContainer.classList.add('hidden');
      if (memberColHeader) memberColHeader.classList.add('hidden');
      loadUserFines();
    } else {
      if (selectContainer) selectContainer.classList.remove('hidden');
      if (memberColHeader) memberColHeader.classList.remove('hidden');
      populateFineMemberDropdown();
      refreshCurrentMemberFines();
    }
  }
}

async function loadInitialAppData() {
  loadBooks();
  if (state.userRole === 'ADMIN') {
    loadMembers();
  }
}

/* -------------------------------------------------------------------------- */
/* 5. DASHBOARD MODULE                                                        */
/* -------------------------------------------------------------------------- */
async function loadDashboardMetrics() {
  const isAdmin = state.userRole === 'ADMIN';

  // Fetch total books count
  const resBooks = await fetchApi(`/books?page=0&size=1`);
  const totalBooks = (resBooks.ok && resBooks.data) ? (resBooks.data.totalElements || '0') : '0';

  const dashTotalBooks = document.getElementById('dashTotalBooks');
  const userDashTotalBooks = document.getElementById('userDashTotalBooks');
  if (dashTotalBooks) dashTotalBooks.textContent = totalBooks;
  if (userDashTotalBooks) userDashTotalBooks.textContent = totalBooks;

  if (isAdmin) {
    // Admin: Fetch total members count
    const dashMembers = document.getElementById('dashTotalMembers');
    const resUsers = await fetchApi(`/user?page=0&size=1`);
    if (resUsers.ok && resUsers.data) {
      if (dashMembers) dashMembers.textContent = resUsers.data.totalElements || '0';
    }
  } else {
    // User: Set Welcome Title
    const userWelcome = document.getElementById('userWelcomeTitle');
    if (userWelcome && state.currentUser) {
      userWelcome.textContent = `Welcome to Athenaeum, ${state.currentUser.name || 'Member'}! 👋`;
    }

    // User: Fetch personal fines
    if (state.currentUser && state.currentUser.id) {
      const resFinesTotal = await fetchApi(`/fines/user/${state.currentUser.id}/pending-total`);
      const userFinesEl = document.getElementById('userDashTotalFines');
      if (resFinesTotal.ok && userFinesEl) {
        const amt = typeof resFinesTotal.data === 'number' ? resFinesTotal.data : parseFloat(resFinesTotal.data) || 0;
        userFinesEl.textContent = `$${amt.toFixed(2)}`;
        userFinesEl.className = amt > 0 ? 'metric-value text-danger' : 'metric-value text-success';
      }
    }

    // User: Fetch membership status
    const userMemStatusEl = document.getElementById('userDashMembershipStatus');
    if (userMemStatusEl) {
      const resMem = await fetchApi('/memberships/me');
      if (resMem.ok && resMem.data && resMem.data.status === 'ACTIVE') {
        state.membershipStatus = 'ACTIVE';
        userMemStatusEl.innerHTML = `<span class="text-success" style="font-size:16px; font-weight:700;">Active Member</span>`;
      } else if (resMem.ok && resMem.data && resMem.data.status === 'PENDING') {
        state.membershipStatus = 'PENDING';
        userMemStatusEl.innerHTML = `<span class="text-warning" style="font-size:16px; font-weight:700;">Pending Signature</span>`;
      } else {
        state.membershipStatus = 'NONE';
        userMemStatusEl.innerHTML = `<span class="text-muted" style="font-size:16px; font-weight:700;">Not Applied</span>`;
      }
    }
  }

  // Render Showcase Grid
  const grid = document.getElementById('dashBookGrid');
  if (state.books && state.books.length > 0) {
    grid.innerHTML = state.books.slice(0, 4).map(renderBookCardHTML).join('');
  } else {
    const res = await fetchApi('/books?page=0&size=4');
    if (res.ok && res.data && res.data.content) {
      grid.innerHTML = res.data.content.map(renderBookCardHTML).join('');
    } else {
      grid.innerHTML = `<div class="text-muted py-4">No catalog books available yet.</div>`;
    }
  }
}

/* -------------------------------------------------------------------------- */
/* 6. BOOKS CATALOG MODULE                                                   */
/* -------------------------------------------------------------------------- */
async function loadBooks() {
  const tbody = document.getElementById('booksTableBody');
  tbody.innerHTML = `<tr><td colspan="5" class="empty-cell">Loading books inventory...</td></tr>`;

  let url = `/books?page=${state.booksPage}&size=${state.booksSize}&sortBy=id&sortDir=asc`;
  if (state.bookSearchQuery) {
    url = `/books/search?query=${encodeURIComponent(state.bookSearchQuery)}&page=${state.booksPage}&size=${state.booksSize}`;
  }

  const res = await fetchApi(url);
  if (!res.ok) {
    tbody.innerHTML = `<tr><td colspan="5" class="empty-cell text-danger">Error loading books catalog (${res.status})</td></tr>`;
    return;
  }

  const paged = res.data || {};
  state.books = paged.content || [];
  state.booksTotalPages = paged.totalPages || 1;
  state.booksTotalElements = paged.totalElements || 0;

  document.getElementById('booksPageInfo').textContent = `Showing page ${paged.pageNo + 1} of ${state.booksTotalPages} (${state.booksTotalElements} titles)`;

  if (state.books.length === 0) {
    tbody.innerHTML = `<tr><td colspan="5" class="empty-cell">No books match your catalog search.</td></tr>`;
    return;
  }

  const isAdmin = state.userRole === 'ADMIN';

  tbody.innerHTML = state.books.map(b => `
    <tr onclick="viewBookDetails(${b.id})" style="cursor: pointer;">
      <td>
        ${renderBookThumbnailHTML(b)}
      </td>
      <td>
        <strong style="color:var(--text-main); font-weight:600;">${escapeHtml(b.title)}</strong>
        <br><span class="text-subtle" style="font-size:11px;">Book ID: #${b.id}</span>
      </td>
      <td>${escapeHtml(b.author)}</td>
      <td><code>${escapeHtml(b.isbn)}</code></td>
      <td class="text-right" onclick="event.stopPropagation()">
        <div class="btn-group justify-end">
          ${isAdmin ? `
            <button class="btn btn-primary btn-sm" onclick="openAdminIssueModalForBook(${b.id})">Borrow</button>
            <button class="btn btn-secondary btn-sm" onclick="openEditBookModal(${JSON.stringify(b).replace(/"/g, '&quot;')})">Edit</button>
            <button class="btn btn-secondary btn-sm" onclick="openUploadCoverModal(${b.id})">Cover</button>
            <button class="btn btn-danger btn-sm" onclick="confirmDeleteBook(${b.id})">Delete</button>
          ` : `
            <button class="btn btn-primary btn-sm" onclick="userBorrowBook(${b.id}, '${escapeHtml(b.title)}')">Borrow</button>
          `}
        </div>
      </td>
    </tr>
  `).join('');
}

function handleBookSearchKeyup(e) {
  if (e.key === 'Enter') executeBookSearch();
}

function executeBookSearch() {
  state.bookSearchQuery = document.getElementById('bookSearchInput').value.trim();
  state.booksPage = 0;
  loadBooks();
}

function resetBookSearch() {
  document.getElementById('bookSearchInput').value = '';
  state.bookSearchQuery = '';
  state.booksPage = 0;
  loadBooks();
}

function changeBookPage(delta) {
  const newPage = state.booksPage + delta;
  if (newPage >= 0 && newPage < state.booksTotalPages) {
    state.booksPage = newPage;
    loadBooks();
  }
}

// Cover image renderer with S3 URL support and fallback
function renderBookThumbnailHTML(book) {
  const monogram = getMonogram(book.title);
  if (book.coverImageUrl) {
    return `<img class="table-thumb-img" src="${escapeHtml(book.coverImageUrl)}" alt="${escapeHtml(book.title)}" onerror="this.outerHTML='<div class=\\'table-thumb\\'>${monogram}</div>'">`;
  }
  return `<div class="table-thumb">${monogram}</div>`;
}

function renderBookCardHTML(book) {
  const monogram = getMonogram(book.title);
  const coverHtml = book.coverImageUrl
    ? `<img class="book-cover-img" src="${escapeHtml(book.coverImageUrl)}" alt="${escapeHtml(book.title)}" onerror="this.outerHTML='<div class=\\'default-cover-placeholder\\'><div class=\\'default-cover-monogram\\'>${monogram}</div><span style=\\'font-size:10px; font-weight:600;\\'>ATHENAEUM</span></div>'">`
    : `<div class="default-cover-placeholder"><div class="default-cover-monogram">${monogram}</div><span style="font-size:10px; font-weight:600;">ATHENAEUM</span></div>`;

  return `
    <div class="book-card" onclick="viewBookDetails(${book.id})" style="cursor: pointer;">
      <div class="book-cover-wrap">
        ${coverHtml}
      </div>
      <div class="book-card-body">
        <div class="book-card-title">${escapeHtml(book.title)}</div>
        <div class="book-card-author">${escapeHtml(book.author)}</div>
        <div class="book-card-isbn">ISBN: ${escapeHtml(book.isbn)}</div>
        <div class="mt-3" onclick="event.stopPropagation()">
          <button class="btn btn-primary btn-sm btn-block" onclick="handleQuickBorrow(${book.id}, '${escapeHtml(book.title)}')">
            Borrow Book
          </button>
        </div>
      </div>
    </div>
  `;
}

function getMonogram(text) {
  if (!text) return 'BK';
  const words = text.split(' ').filter(w => w.length > 0);
  if (words.length >= 2) {
    return (words[0][0] + words[1][0]).toUpperCase();
  }
  return text.substring(0, 2).toUpperCase();
}

async function handleQuickBorrow(bookId, bookTitle) {
  if (state.userRole === 'ADMIN') {
    openAdminIssueModalForBook(bookId);
  } else {
    userBorrowBook(bookId, bookTitle);
  }
}

async function openAdminIssueModalForBook(bookId) {
  await populateBorrowDropdowns();
  const bookSelect = document.getElementById('adminModalBorrowBookSelect');
  if (bookSelect) {
    bookSelect.value = bookId;
  }
  openModal('adminIssueBorrowModal');
}

// Book CRUD Actions
async function handleCreateBookSubmit(e) {
  e.preventDefault();
  const title = document.getElementById('newBookTitle').value.trim();
  const author = document.getElementById('newBookAuthor').value.trim();
  const isbn = document.getElementById('newBookIsbn').value.trim();

  const res = await fetchApi('/books', {
    method: 'POST',
    body: JSON.stringify({ title, author, isbn })
  });

  if (res.ok) {
    showToast('New book added to inventory', 'success');
    closeModal('createBookModal');
    document.getElementById('newBookTitle').value = '';
    document.getElementById('newBookAuthor').value = '';
    document.getElementById('newBookIsbn').value = '';
    loadBooks();
  } else {
    showToast(`Failed to create book (${res.status})`, 'error');
  }
}

async function viewBookDetails(bookId) {
  const res = await fetchApi(`/books/${bookId}`);
  if (!res.ok) {
    showToast(`Failed to load book details (${res.status || 'Network error'})`, 'error');
    return;
  }

  const b = res.data;
  const monogram = getMonogram(b.title);
  const coverHtml = b.coverImageUrl
    ? `<img src="${escapeHtml(b.coverImageUrl)}" alt="${escapeHtml(b.title)}" style="width:100%; height:100%; object-fit:cover; border-radius:var(--radius-md);" onerror="this.outerHTML='<div class=\\'default-cover-placeholder\\'><div class=\\'default-cover-monogram\\' style=\\'font-size:36px;\\'>${monogram}</div><span style=\\'font-size:12px; font-weight:700; color:var(--accent-primary);\\'>ATHENAEUM CATALOG</span></div>'">`
    : `<div class="default-cover-placeholder"><div class="default-cover-monogram" style="font-size:36px;">${monogram}</div><span style="font-size:12px; font-weight:700; color:var(--accent-primary);">ATHENAEUM CATALOG</span></div>`;

  const drawerBody = document.getElementById('bookDetailsBody');
  drawerBody.innerHTML = `
    <div class="book-cover-wrap mb-4" style="height:220px; border-radius:var(--radius-md);">
      ${coverHtml}
    </div>

    <h2 style="font-size:20px; margin-bottom:4px;">${escapeHtml(b.title)}</h2>
    <p class="text-muted mb-4">By ${escapeHtml(b.author)}</p>

    <div class="form-group">
      <label class="form-label">ISBN Number</label>
      <div class="form-input" style="background:var(--bg-app);">${escapeHtml(b.isbn)}</div>
    </div>

    <div class="form-group">
      <label class="form-label">Database Record ID</label>
      <div class="form-input" style="background:var(--bg-app);">#${b.id}</div>
    </div>

    ${state.userRole === 'ADMIN' ? `
      <button class="btn btn-primary btn-block mt-6" onclick="closeDrawer('bookDetailsDrawer'); openAdminIssueModalForBook(${b.id})">Borrow / Issue Book</button>
      <div class="btn-group mt-3">
        <button class="btn btn-secondary flex-1" onclick="openEditBookModal(${JSON.stringify(b).replace(/"/g, '&quot;')})">Edit Book</button>
        <button class="btn btn-secondary flex-1" onclick="openUploadCoverModal(${b.id})">Upload Cover</button>
      </div>
      <button class="btn btn-danger btn-block mt-3" onclick="confirmDeleteBook(${b.id})">Delete Book Title</button>
    ` : `
      <button class="btn btn-primary btn-block mt-6" onclick="userBorrowBook(${b.id}, '${escapeHtml(b.title)}')">Borrow Book</button>
    `}
  `;

  openDrawer('bookDetailsDrawer');
}

function openEditBookModal(book) {
  closeDrawer('bookDetailsDrawer');
  document.getElementById('editBookId').value = book.id;
  document.getElementById('editBookTitle').value = book.title;
  document.getElementById('editBookAuthor').value = book.author;
  document.getElementById('editBookIsbn').value = book.isbn;
  openModal('editBookModal');
}

async function handleEditBookSubmit(e) {
  e.preventDefault();
  const id = document.getElementById('editBookId').value;
  const title = document.getElementById('editBookTitle').value.trim();
  const author = document.getElementById('editBookAuthor').value.trim();
  const isbn = document.getElementById('editBookIsbn').value.trim();

  const res = await fetchApi(`/books/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ title, author, isbn })
  });

  if (res.ok) {
    showToast('Book details updated', 'success');
    closeModal('editBookModal');
    loadBooks();
  } else {
    showToast(`Failed to update book (${res.status})`, 'error');
  }
}

function confirmDeleteBook(id) {
  closeDrawer('bookDetailsDrawer');
  showConfirmModal('Delete Book Title', `Are you sure you want to permanently delete Book ID #${id}?`, async () => {
    const res = await fetchApi(`/books/${id}`, { method: 'DELETE' });
    if (res.ok || res.status === 204) {
      showToast('Book deleted from catalog', 'success');
      loadBooks();
    } else {
      showToast(`Delete failed (${res.status})`, 'error');
    }
  });
}

function openUploadCoverModal(bookId) {
  closeDrawer('bookDetailsDrawer');
  document.getElementById('uploadCoverBookId').value = bookId;
  document.getElementById('uploadCoverBookIdDisplay').textContent = bookId;
  document.getElementById('coverFileInput').value = '';
  document.getElementById('coverPreviewContainer').classList.add('hidden');
  openModal('uploadCoverModal');
}

function handleCoverFileSelected(e) {
  const file = e.target.files[0];
  if (!file) return;

  const reader = new FileReader();
  reader.onload = (event) => {
    document.getElementById('coverPreviewImg').src = event.target.result;
    document.getElementById('coverPreviewContainer').classList.remove('hidden');
  };
  reader.readAsDataURL(file);
}

async function handleUploadCoverSubmit(e) {
  e.preventDefault();
  const bookId = document.getElementById('uploadCoverBookId').value;
  const fileInput = document.getElementById('coverFileInput');
  const file = fileInput.files[0];

  if (!file) {
    showToast('Please select an image file', 'error');
    return;
  }

  const formData = new FormData();
  formData.append('file', file);

  showToast('Uploading book cover to AWS S3...', 'info');

  const res = await fetchApi(`/books/${bookId}/cover`, {
    method: 'POST',
    body: formData
  });

  if (res.ok) {
    showToast('Book cover uploaded and updated successfully!', 'success');
    closeModal('uploadCoverModal');
    loadBooks();
  } else {
    showToast(`Failed to upload book cover (${res.status})`, 'error');
  }
}

/* -------------------------------------------------------------------------- */
/* 7. MEMBERS MODULE ("Members")                                              */
/* -------------------------------------------------------------------------- */
async function loadMembers() {
  const tbody = document.getElementById('membersTableBody');
  tbody.innerHTML = `<tr><td colspan="4" class="empty-cell">Loading registered members...</td></tr>`;

  let url = `/user?page=${state.membersPage}&size=${state.membersSize}&sortBy=id&sortDir=asc`;
  if (state.memberSearchQuery) {
    url = `/user/search?query=${encodeURIComponent(state.memberSearchQuery)}&page=${state.membersPage}&size=${state.membersSize}`;
  }

  const res = await fetchApi(url);
  if (!res.ok) {
    tbody.innerHTML = `<tr><td colspan="4" class="empty-cell text-danger">Error loading members (${res.status})</td></tr>`;
    return;
  }

  const paged = res.data || {};
  state.members = paged.content || [];
  state.membersTotalPages = paged.totalPages || 1;
  state.membersTotalElements = paged.totalElements || 0;

  document.getElementById('membersPageInfo').textContent = `Showing page ${paged.pageNo + 1} of ${state.membersTotalPages} (${state.membersTotalElements} members)`;

  if (state.members.length === 0) {
    tbody.innerHTML = `<tr><td colspan="4" class="empty-cell">No members match your search.</td></tr>`;
    return;
  }

  tbody.innerHTML = state.members.map(m => `
    <tr>
      <td><strong>#${m.id}</strong></td>
      <td>
        <strong>${escapeHtml(m.name || 'Member')}</strong>
      </td>
      <td>${escapeHtml(m.email)}</td>
      <td class="text-right">
        <div class="btn-group justify-end">
          <button class="btn btn-secondary btn-sm" onclick="viewMemberDetails(${m.id})">Profile</button>
          <button class="btn btn-secondary btn-sm" onclick="openEditMemberModal(${JSON.stringify(m).replace(/"/g, '&quot;')})">Edit</button>
          <button class="btn btn-danger btn-sm" onclick="confirmDeleteMember(${m.id})">Delete</button>
        </div>
      </td>
    </tr>
  `).join('');
}

function handleMemberSearchKeyup(e) {
  if (e.key === 'Enter') executeMemberSearch();
}

function executeMemberSearch() {
  state.memberSearchQuery = document.getElementById('memberSearchInput').value.trim();
  state.membersPage = 0;
  loadMembers();
}

function resetMemberSearch() {
  document.getElementById('memberSearchInput').value = '';
  state.memberSearchQuery = '';
  state.membersPage = 0;
  loadMembers();
}

function changeMemberPage(delta) {
  const newPage = state.membersPage + delta;
  if (newPage >= 0 && newPage < state.membersTotalPages) {
    state.membersPage = newPage;
    loadMembers();
  }
}

async function handleCreateUserSubmit(e) {
  e.preventDefault();
  const name = document.getElementById('newUserName').value.trim();
  const email = document.getElementById('newUserEmail').value.trim();
  const password = document.getElementById('newUserPassword').value;

  const res = await fetchApi('/user', {
    method: 'POST',
    body: JSON.stringify({ name, email, password })
  });

  if (res.ok) {
    showToast('Library member registered', 'success');
    closeModal('createUserModal');
    document.getElementById('newUserName').value = '';
    document.getElementById('newUserEmail').value = '';
    document.getElementById('newUserPassword').value = '';
    loadMembers();
  } else {
    showToast(`Failed to register member (${res.status})`, 'error');
  }
}

function openEditMemberModal(member) {
  closeDrawer('memberDetailsDrawer');
  document.getElementById('editMemberId').value = member.id;
  document.getElementById('editMemberName').value = member.name || '';
  document.getElementById('editMemberEmail').value = member.email || '';
  document.getElementById('editMemberPassword').value = '';
  openModal('editMemberModal');
}

async function handleEditMemberSubmit(e) {
  e.preventDefault();
  const id = document.getElementById('editMemberId').value;
  const name = document.getElementById('editMemberName').value.trim();
  const email = document.getElementById('editMemberEmail').value.trim();
  const password = document.getElementById('editMemberPassword').value;

  const payload = { name, email };
  if (password && password.trim().length > 0) {
    payload.password = password;
  }

  const res = await fetchApi(`/user/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });

  if (res.ok) {
    showToast('Member profile updated successfully', 'success');
    closeModal('editMemberModal');
    loadMembers();
    if (state.currentUser && state.currentUser.id == id) {
      checkAuthSession();
    }
  } else {
    showToast(`Failed to update member (${res.status})`, 'error');
  }
}

async function viewMemberDetails(memberId) {
  const resUser = await fetchApi(`/user/${memberId}`);
  if (!resUser.ok) {
    showToast(`Failed to load member profile (${resUser.status || 'Network error'})`, 'error');
    return;
  }

  const m = resUser.data;
  
  // Load member pending fines sum
  let pendingAmount = 0;
  try {
    const resPendingTotal = await fetchApi(`/fines/user/${memberId}/pending-total`);
    if (resPendingTotal.ok) {
      pendingAmount = typeof resPendingTotal.data === 'number' ? resPendingTotal.data : parseFloat(resPendingTotal.data) || 0;
    }
  } catch (e) {
    console.warn('Could not fetch member pending fine', e);
  }

  const drawerBody = document.getElementById('memberDetailsBody');
  drawerBody.innerHTML = `
    <div class="user-avatar mb-4" style="width:56px; height:56px; font-size:20px; margin:0 auto 16px auto;">
      ${getMonogram(m.name || m.email)}
    </div>
    <h2 class="text-center" style="font-size:20px; margin-bottom:2px;">${escapeHtml(m.name || 'Member')}</h2>
    <p class="text-center text-muted mb-6">${escapeHtml(m.email)}</p>

    <div class="section-card mb-4">
      <span class="text-subtle" style="font-size:11px; font-weight:700; text-transform:uppercase;">Outstanding Fine Balance</span>
      <div style="font-size:24px; font-weight:800; color:${pendingAmount > 0 ? 'var(--badge-danger-text)' : 'var(--badge-success-text)'}; margin-top:4px;">
        $${pendingAmount.toFixed(2)}
      </div>
    </div>

    <div class="form-group">
      <label class="form-label">Member ID</label>
      <div class="form-input" style="background:var(--bg-app);">#${m.id}</div>
    </div>

    <div class="btn-group mt-6">
      <button class="btn btn-primary flex-1" onclick="openEditMemberModal(${JSON.stringify(m).replace(/"/g, '&quot;')})">Edit Profile</button>
      <button class="btn btn-secondary flex-1" onclick="navigateToMemberFines(${m.id})">Fines</button>
      <button class="btn btn-danger btn-sm" onclick="confirmDeleteMember(${m.id})">Delete</button>
    </div>
  `;

  openDrawer('memberDetailsDrawer');
}

function navigateToMemberFines(memberId) {
  closeDrawer('memberDetailsDrawer');
  navigateTo('fines');
  setTimeout(() => {
    const select = document.getElementById('fineMemberSelect');
    if (select) {
      select.value = memberId;
      handleFineMemberChange(memberId);
    }
  }, 100);
}

function confirmDeleteMember(id) {
  closeDrawer('memberDetailsDrawer');
  showConfirmModal('Delete Member Profile', `Are you sure you want to remove Member ID #${id}?`, async () => {
    const res = await fetchApi(`/user/${id}`, { method: 'DELETE' });
    if (res.ok || res.status === 204) {
      showToast('Member profile deleted', 'success');
      loadMembers();
    } else {
      showToast(`Delete member failed (${res.status})`, 'error');
    }
  });
}

/* -------------------------------------------------------------------------- */
/* 8. BORROWED BOOKS MODULE                                                   */
/* -------------------------------------------------------------------------- */
async function loadBorrows() {
  const isAdmin = state.userRole === 'ADMIN';

  if (isAdmin) {
    const tbody = document.getElementById('adminBorrowsTableBody');
    if (!tbody) return;
    tbody.innerHTML = `<tr><td colspan="7" class="empty-cell">Loading library circulation records...</td></tr>`;

    const res = await fetchApi('/borrow');
    if (!res.ok) {
      tbody.innerHTML = `<tr><td colspan="7" class="empty-cell text-danger">Error loading borrow records (${res.status})</td></tr>`;
      return;
    }

    const borrows = Array.isArray(res.data) ? res.data : [];
    if (borrows.length === 0) {
      tbody.innerHTML = `<tr><td colspan="7" class="empty-cell">No active or past book loans recorded.</td></tr>`;
      return;
    }

    tbody.innerHTML = borrows.map(b => {
      const isReturned = b.status === 'RETURNED' || !!b.returnedDate;
      const isOverdue = !isReturned && b.dueDate && new Date(b.dueDate) < new Date();
      return `
        <tr>
          <td>
            ${renderBookThumbnailHTML({ coverImageUrl: b.bookCoverImageUrl, title: b.bookTitle || 'Book' })}
          </td>
          <td>
            <strong>${escapeHtml(b.bookTitle || 'Book Title')}</strong>
            <br><span class="text-subtle" style="font-size:11px;">By ${escapeHtml(b.bookAuthor || 'Unknown Author')} &bull; Loan #${b.id}</span>
          </td>
          <td>
            <strong>${escapeHtml(b.userName || 'Library Member')}</strong>
            <br><span class="text-subtle" style="font-size:11px;">${escapeHtml(b.userEmail || '')}</span>
          </td>
          <td>${b.borrowDate || 'N/A'}</td>
          <td>
            <span class="${isOverdue ? 'text-danger' : ''}" style="font-weight:${isOverdue ? '700' : 'normal'};">
              ${b.dueDate || 'N/A'} ${isOverdue ? '(Overdue)' : ''}
            </span>
          </td>
          <td>
            <span class="badge ${isReturned ? 'badge-success' : (isOverdue ? 'badge-danger' : 'badge-warning')}">
              ${isReturned ? 'RETURNED' : (isOverdue ? 'OVERDUE' : 'BORROWED')}
            </span>
          </td>
          <td class="text-right">
            ${!isReturned ? `
              <button class="btn btn-secondary btn-sm" onclick="confirmReturnBook(${b.id})">Return Book</button>
            ` : `<span class="text-muted" style="font-size:12px;">Returned</span>`}
          </td>
        </tr>
      `;
    }).join('');

  } else {
    // User / Member View
    const tbody = document.getElementById('userBorrowsTableBody');
    if (!tbody) return;
    tbody.innerHTML = `<tr><td colspan="7" class="empty-cell">Loading your borrowed books...</td></tr>`;

    let res = await fetchApi('/borrow/me');
    if (!res.ok && state.currentUser && state.currentUser.id) {
      res = await fetchApi(`/borrow/user/${state.currentUser.id}`);
    }

    if (!res.ok) {
      tbody.innerHTML = `<tr><td colspan="7" class="empty-cell text-danger">Error loading borrowed books (${res.status})</td></tr>`;
      return;
    }

    const borrows = Array.isArray(res.data) ? res.data : [];
    if (borrows.length === 0) {
      tbody.innerHTML = `<tr><td colspan="7" class="empty-cell">You have no borrowed books currently on loan.</td></tr>`;
      return;
    }

    tbody.innerHTML = borrows.map(b => {
      const isReturned = b.status === 'RETURNED' || !!b.returnedDate;
      const isOverdue = !isReturned && b.dueDate && new Date(b.dueDate) < new Date();
      return `
        <tr>
          <td>
            ${renderBookThumbnailHTML({ coverImageUrl: b.bookCoverImageUrl, title: b.bookTitle || 'Book' })}
          </td>
          <td>
            <strong>${escapeHtml(b.bookTitle || 'Book Title')}</strong>
            <br><span class="text-subtle" style="font-size:11px;">Loan Record #${b.id}</span>
          </td>
          <td>${escapeHtml(b.bookAuthor || 'Unknown')}</td>
          <td>${b.borrowDate || 'N/A'}</td>
          <td>
            <span class="${isOverdue ? 'text-danger' : ''}" style="font-weight:${isOverdue ? '700' : 'normal'};">
              ${b.dueDate || 'N/A'} ${isOverdue ? '(Overdue)' : ''}
            </span>
          </td>
          <td>
            <span class="badge ${isReturned ? 'badge-success' : (isOverdue ? 'badge-danger' : 'badge-warning')}">
              ${isReturned ? 'RETURNED' : (isOverdue ? 'OVERDUE' : 'BORROWED')}
            </span>
          </td>
          <td class="text-right">
            ${!isReturned ? `
              <button class="btn btn-secondary btn-sm" onclick="confirmReturnBook(${b.id})">Return Book</button>
            ` : `<span class="text-muted" style="font-size:12px;">Returned</span>`}
          </td>
        </tr>
      `;
    }).join('');
  }
}

async function populateBorrowDropdowns() {
  const adminMemberSelect = document.getElementById('adminModalBorrowMemberSelect');
  const adminBookSelect = document.getElementById('adminModalBorrowBookSelect');
  const userBookSelect = document.getElementById('userModalBorrowBookSelect');
  const notice = document.getElementById('borrowMembershipNotice');

  if (adminMemberSelect) adminMemberSelect.innerHTML = `<option value="">-- Choose Member --</option>`;
  if (adminBookSelect) adminBookSelect.innerHTML = `<option value="">-- Choose Book --</option>`;
  if (userBookSelect) userBookSelect.innerHTML = `<option value="">-- Choose Book --</option>`;

  // Check Membership status for Users
  if (state.userRole !== 'ADMIN') {
    const resMem = await fetchApi('/memberships/me');
    if (resMem.ok && resMem.data && resMem.data.status === 'ACTIVE') {
      state.membershipStatus = 'ACTIVE';
      if (notice) notice.classList.add('hidden');
    } else {
      state.membershipStatus = resMem.data ? resMem.data.status : 'NONE';
      if (notice) notice.classList.remove('hidden');
    }
  }

  // Populate Members (Admin modal)
  if (state.userRole === 'ADMIN' && adminMemberSelect) {
    const resUsers = await fetchApi('/user?page=0&size=50');
    if (resUsers.ok && resUsers.data && resUsers.data.content) {
      resUsers.data.content.forEach(u => {
        adminMemberSelect.innerHTML += `<option value="${u.id}">#${u.id} - ${escapeHtml(u.name || u.email)}</option>`;
      });
    }
  }

  // Populate Books (Both Admin & User dropdowns)
  const resBooks = await fetchApi('/books?page=0&size=50');
  if (resBooks.ok && resBooks.data && resBooks.data.content) {
    resBooks.data.content.forEach(b => {
      const opt = `<option value="${b.id}">#${b.id} - ${escapeHtml(b.title)} (${escapeHtml(b.author)})</option>`;
      if (adminBookSelect) adminBookSelect.innerHTML += opt;
      if (userBookSelect) userBookSelect.innerHTML += opt;
    });
  }
}

async function handleIssueLoanSubmit(e) {
  e.preventDefault();
  const userId = parseInt(document.getElementById('adminModalBorrowMemberSelect').value, 10);
  const bookId = parseInt(document.getElementById('adminModalBorrowBookSelect').value, 10);

  if (!userId || !bookId) {
    showToast('Please select both a member and a book title', 'error');
    return;
  }

  const res = await fetchApi('/borrow', {
    method: 'POST',
    body: JSON.stringify({ userId, bookId })
  });

  if (res.ok && res.data) {
    showToast('Book borrow issued successfully!', 'success');
    closeModal('adminIssueBorrowModal');
    loadBorrows();
    if (state.currentPage === 'dashboard') loadDashboardMetrics();
  } else {
    const errorMsg = res.data && res.data.message ? res.data.message : `Error issuing borrow (${res.status})`;
    showToast(errorMsg, 'error');
  }
}

async function handleUserSelfBorrowSubmit(e) {
  e.preventDefault();
  if (!state.currentUser || !state.currentUser.id) {
    showToast('User profile not loaded', 'error');
    return;
  }

  const bookId = parseInt(document.getElementById('userModalBorrowBookSelect').value, 10);
  if (!bookId) {
    showToast('Please select a book title to borrow', 'error');
    return;
  }

  // Validate active membership
  if (state.membershipStatus !== 'ACTIVE') {
    const resMem = await fetchApi('/memberships/me');
    if (resMem.ok && resMem.data && resMem.data.status === 'ACTIVE') {
      state.membershipStatus = 'ACTIVE';
    } else {
      closeModal('userSelfBorrowModal');
      showToast('Active membership required to borrow books. Please activate your membership.', 'error');
      navigateTo('membership');
      return;
    }
  }

  const res = await fetchApi('/borrow', {
    method: 'POST',
    body: JSON.stringify({ userId: state.currentUser.id, bookId: bookId })
  });

  if (res.ok && res.data) {
    showToast('Book checked out successfully!', 'success');
    closeModal('userSelfBorrowModal');
    loadBorrows();
    if (state.currentPage === 'dashboard') loadDashboardMetrics();
  } else {
    const errorMsg = res.data && res.data.message ? res.data.message : `Error borrowing book (${res.status})`;
    if (res.status === 403 || (errorMsg && errorMsg.toLowerCase().includes('membership'))) {
      closeModal('userSelfBorrowModal');
      showToast('Active membership required to borrow books', 'error');
      navigateTo('membership');
    } else {
      showToast(errorMsg, 'error');
    }
  }
}

function confirmReturnBook(borrowId) {
  showConfirmModal('Return Borrowed Book', `Are you sure you want to mark Loan Record #${borrowId} as RETURNED?`, async () => {
    const res = await fetchApi(`/borrow/${borrowId}`, { method: 'PATCH' });
    if (res.ok) {
      showToast('Book returned successfully!', 'success');
      loadBorrows();
      if (state.currentPage === 'dashboard') loadDashboardMetrics();
    } else {
      const errorMsg = res.data && res.data.message ? res.data.message : `Return failed (${res.status})`;
      showToast(errorMsg, 'error');
    }
  });
}

/* -------------------------------------------------------------------------- */
/* 9. FINES MODULE                                                            */
/* -------------------------------------------------------------------------- */
async function populateFineMemberDropdown() {
  const select = document.getElementById('fineMemberSelect');
  if (!select) return;
  select.innerHTML = `<option value="">-- All Library Members (All Fines) --</option>`;

  const res = await fetchApi('/user?page=0&size=100');
  if (res.ok && res.data && res.data.content) {
    res.data.content.forEach(u => {
      select.innerHTML += `<option value="${u.id}">#${u.id} - ${escapeHtml(u.name || u.email)}</option>`;
    });
  }
}

async function handleFineMemberChange(memberId) {
  state.currentFineMemberId = memberId;
  refreshCurrentMemberFines();
}

async function loadUserFines() {
  const tbody = document.getElementById('finesTableBody');
  const scopeEl = document.getElementById('fineMemberName');
  const balanceEl = document.getElementById('fineTotalBalance');

  if (tbody) tbody.innerHTML = `<tr><td colspan="5" class="empty-cell">Loading your fine records...</td></tr>`;
  if (scopeEl) scopeEl.textContent = state.currentUser ? (state.currentUser.name || state.currentUser.email) : 'My Account';

  let res = await fetchApi('/fines/me');
  if (!res.ok && state.currentUser && state.currentUser.id) {
    res = await fetchApi(`/fines/user/${state.currentUser.id}`);
  }

  if (!res.ok) {
    if (tbody) tbody.innerHTML = `<tr><td colspan="5" class="empty-cell text-danger">Error loading fines (${res.status})</td></tr>`;
    return;
  }

  const fines = Array.isArray(res.data) ? res.data : [];
  
  // Calculate total pending
  const pendingTotal = fines.filter(f => f.status === 'PENDING' || !f.status).reduce((acc, f) => {
    const val = f.amount != null ? f.amount : (f.pendingFineAmount || 0);
    return acc + (typeof val === 'number' ? val : parseFloat(val) || 0);
  }, 0);

  if (balanceEl) balanceEl.textContent = `$${pendingTotal.toFixed(2)}`;

  if (fines.length === 0) {
    if (tbody) tbody.innerHTML = `<tr><td colspan="5" class="empty-cell">No fine records associated with your account.</td></tr>`;
    return;
  }

  if (tbody) {
    tbody.innerHTML = fines.map(f => {
      const isPaid = f.status === 'PAID';
      const amt = f.amount != null ? f.amount : (f.pendingFineAmount || 0);
      const numAmt = typeof amt === 'number' ? amt : parseFloat(amt) || 0;
      return `
        <tr>
          <td><strong>#${f.id}</strong></td>
          <td>
            <strong>${escapeHtml(f.bookTitle || 'Library Book')}</strong>
            <br><span class="text-subtle" style="font-size:11px;">By ${escapeHtml(f.bookAuthor || 'Unknown')}</span>
          </td>
          <td><strong style="color:${isPaid ? 'var(--text-main)' : 'var(--badge-danger-text)'};">$${numAmt.toFixed(2)}</strong></td>
          <td>
            <span class="badge ${isPaid ? 'badge-success' : 'badge-danger'}">${isPaid ? 'PAID' : 'PENDING'}</span>
          </td>
          <td class="text-right">
            ${!isPaid ? `<button class="btn btn-primary btn-sm" onclick="confirmPayFine(${f.id})">Pay Fine</button>` : `<span class="text-muted" style="font-size:12px;">Settled</span>`}
          </td>
        </tr>
      `;
    }).join('');
  }
}

async function refreshCurrentMemberFines() {
  const isAdmin = state.userRole === 'ADMIN';
  if (!isAdmin) {
    loadUserFines();
    return;
  }

  const memberId = state.currentFineMemberId;
  const tbody = document.getElementById('finesTableBody');
  const scopeEl = document.getElementById('fineMemberName');
  const balanceEl = document.getElementById('fineTotalBalance');

  if (tbody) tbody.innerHTML = `<tr><td colspan="6" class="empty-cell">Loading library fine records...</td></tr>`;

  let url = '/fines';
  if (memberId) {
    url = `/fines/user/${memberId}`;
  }

  const res = await fetchApi(url);
  if (!res.ok) {
    if (tbody) tbody.innerHTML = `<tr><td colspan="6" class="empty-cell text-danger">Error loading fines (${res.status})</td></tr>`;
    return;
  }

  const fines = Array.isArray(res.data) ? res.data : [];

  // Update Scope Label
  if (scopeEl) {
    if (!memberId) {
      scopeEl.textContent = 'All Library Members (All Fines)';
    } else {
      const select = document.getElementById('fineMemberSelect');
      const selectedText = select && select.options[select.selectedIndex] ? select.options[select.selectedIndex].text : `Member #${memberId}`;
      scopeEl.textContent = selectedText;
    }
  }

  // Calculate Total Outstanding
  const pendingTotal = fines.filter(f => f.status === 'PENDING' || !f.status).reduce((acc, f) => {
    const val = f.amount != null ? f.amount : (f.pendingFineAmount || 0);
    return acc + (typeof val === 'number' ? val : parseFloat(val) || 0);
  }, 0);

  if (balanceEl) balanceEl.textContent = `$${pendingTotal.toFixed(2)}`;

  if (fines.length === 0) {
    if (tbody) tbody.innerHTML = `<tr><td colspan="6" class="empty-cell">No fine records found for the selected scope.</td></tr>`;
    return;
  }

  if (tbody) {
    tbody.innerHTML = fines.map(f => {
      const isPaid = f.status === 'PAID';
      const amt = f.amount != null ? f.amount : (f.pendingFineAmount || 0);
      const numAmt = typeof amt === 'number' ? amt : parseFloat(amt) || 0;
      return `
        <tr>
          <td><strong>#${f.id}</strong></td>
          <td>
            <strong>${escapeHtml(f.userName || 'Library Member')}</strong>
            <br><span class="text-subtle" style="font-size:11px;">${escapeHtml(f.userEmail || '')}</span>
          </td>
          <td>
            <strong>${escapeHtml(f.bookTitle || 'Library Book')}</strong>
            <br><span class="text-subtle" style="font-size:11px;">By ${escapeHtml(f.bookAuthor || 'Unknown')}</span>
          </td>
          <td><strong style="color:${isPaid ? 'var(--text-main)' : 'var(--badge-danger-text)'};">$${numAmt.toFixed(2)}</strong></td>
          <td>
            <span class="badge ${isPaid ? 'badge-success' : 'badge-danger'}">${isPaid ? 'PAID' : 'PENDING'}</span>
          </td>
          <td class="text-right">
            ${!isPaid ? `<button class="btn btn-primary btn-sm" onclick="confirmPayFine(${f.id})">Settle Payment</button>` : `<span class="text-muted" style="font-size:12px;">Settled</span>`}
          </td>
        </tr>
      `;
    }).join('');
  }
}

function confirmPayFine(fineId) {
  showConfirmModal('Settle Fine Payment', `Are you sure you want to mark Fine Record #${fineId} as PAID?`, async () => {
    const res = await fetchApi(`/fines/${fineId}/pay`, { method: 'POST' });
    if (res.ok) {
      showToast(`Fine #${fineId} settled successfully!`, 'success');
      if (state.userRole === 'ADMIN') {
        refreshCurrentMemberFines();
      } else {
        loadUserFines();
      }
      if (state.currentPage === 'dashboard') loadDashboardMetrics();
    } else {
      showToast(`Payment settlement failed (${res.status})`, 'error');
    }
  });
}

/* -------------------------------------------------------------------------- */
/* 10. GLOBAL SEARCH HANDLER                                                 */
/* -------------------------------------------------------------------------- */
function handleGlobalSearchKeyup(e) {
  if (e.key === 'Enter') {
    const q = e.target.value.trim();
    if (!q) return;

    if (state.currentPage === 'members') {
      document.getElementById('memberSearchInput').value = q;
      executeMemberSearch();
    } else {
      navigateTo('books');
      document.getElementById('bookSearchInput').value = q;
      executeBookSearch();
    }
  }
}

function toggleUserDropdown(e) {
  e.stopPropagation();
  const dropdown = document.getElementById('userDropdown');
  dropdown.classList.toggle('hidden');
}

document.addEventListener('click', () => {
  const dropdown = document.getElementById('userDropdown');
  if (dropdown && !dropdown.classList.contains('hidden')) {
    dropdown.classList.add('hidden');
  }
});

/* -------------------------------------------------------------------------- */
/* 11. UI MODALS, DRAWERS & CONFIRMATION                                       */
/* -------------------------------------------------------------------------- */
function openModal(id) {
  const m = document.getElementById(id);
  if (m) m.classList.add('open');
}

function closeModal(id) {
  const m = document.getElementById(id);
  if (m) m.classList.remove('open');
}

function closeModalOnBackdrop(e, id) {
  if (e.target.id === id) closeModal(id);
}

function openDrawer(id) {
  const d = document.getElementById(id);
  if (d) d.classList.add('open');
}

function closeDrawer(id) {
  const d = document.getElementById(id);
  if (d) d.classList.remove('open');
}

function closeDrawerOnBackdrop(e, id) {
  if (e.target.id === id) closeDrawer(id);
}

function showConfirmModal(title, message, onConfirm) {
  document.getElementById('confirmTitle').textContent = title;
  document.getElementById('confirmMessage').textContent = message;
  state.confirmCallback = onConfirm;
  openModal('confirmModal');
}

function closeConfirmModal(isConfirmed) {
  closeModal('confirmModal');
  if (isConfirmed && typeof state.confirmCallback === 'function') {
    state.confirmCallback();
  }
  state.confirmCallback = null;
}

function showToast(message, type = 'info') {
  const container = document.getElementById('toastContainer');
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.innerHTML = `<span>${escapeHtml(message)}</span>`;
  container.appendChild(toast);

  setTimeout(() => {
    toast.remove();
  }, 3500);
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

/* -------------------------------------------------------------------------- */
/* MEMBERSHIP MODULE LOGIC                                                    */
/* -------------------------------------------------------------------------- */
async function loadMembershipStatus() {
  // Hide all sub-states first
  document.getElementById('membership-state-none').classList.add('hidden');
  document.getElementById('membership-state-pending').classList.add('hidden');
  document.getElementById('membership-state-active').classList.add('hidden');

  const res = await fetchApi('/memberships/me');
  if (res.status === 404) {
    // Not applied
    state.membershipStatus = 'NONE';
    document.getElementById('membership-state-none').classList.remove('hidden');
    return;
  }

  if (!res.ok) {
    showToast('Failed to fetch membership status', 'error');
    return;
  }

  const membership = res.data;
  state.currentMembershipUuid = membership.uuid;
  state.currentMembershipId = membership.membershipId;
  state.membershipStatus = membership.status;

  if (membership.status === 'PENDING') {
    document.getElementById('membership-state-pending').classList.remove('hidden');
    document.getElementById('btnActivateMembership').disabled = true;
    document.getElementById('signatureFile').value = '';
    document.getElementById('fileNameDisplay').textContent = 'Click to select PNG signature file';
    document.getElementById('sigPreviewContainer').classList.add('hidden');
    loadAgreementTemplate();
  } else if (membership.status === 'ACTIVE') {
    document.getElementById('membership-state-active').classList.remove('hidden');
    
    document.getElementById('activeCardId').textContent = membership.membershipId;
    document.getElementById('activeCardName').textContent = state.currentUser ? state.currentUser.name : 'Library Member';
    
    // Format Expiry Date
    if (membership.expiryDate) {
      document.getElementById('activeCardExpiry').textContent = membership.expiryDate;
    } else {
      document.getElementById('activeCardExpiry').textContent = 'N/A';
    }

    // Format Activated Date
    if (membership.activatedAt) {
      const activeDate = new Date(membership.activatedAt);
      document.getElementById('activeCardActivatedDate').textContent = activeDate.toLocaleDateString();
    } else {
      document.getElementById('activeCardActivatedDate').textContent = 'N/A';
    }
  }
}

async function applyForMembership() {
  const res = await fetchApi('/memberships', { method: 'POST' });
  if (res.ok) {
    showToast('Membership application submitted successfully', 'success');
    loadMembershipStatus();
  } else {
    const errorMsg = res.data && res.data.message ? res.data.message : 'Error submitting membership application';
    showToast(errorMsg, 'error');
  }
}

async function loadAgreementTemplate() {
  const container = document.getElementById('agreementTextContainer');
  container.innerHTML = '<p class="text-muted">Loading agreement terms...</p>';

  if (!state.currentMembershipId) return;

  const res = await fetchApi(`/memberships/${state.currentMembershipId}/agreement`);
  if (res.ok) {
    let html = res.data;
    // Replace text placeholders for display
    if (state.currentUser) {
      html = html.replace('{{memberName}}', state.currentUser.name)
                 .replace('{{memberEmail}}', state.currentUser.email);
    }
    html = html.replace('{{membershipId}}', state.currentMembershipId)
               .replace('{{applicationDate}}', new Date().toLocaleDateString());
    
    container.innerHTML = html;
  } else {
    container.innerHTML = '<p class="text-danger">Failed to load membership agreement terms.</p>';
  }
}

function handleSignatureFileChange(e) {
  const file = e.target.files[0];
  if (!file) return;

  if (file.type !== 'image/png') {
    showToast('Signature file must be a PNG image', 'error');
    e.target.value = '';
    return;
  }

  if (file.size > 50 * 1024) {
    showToast('Signature file size must not exceed 50KB', 'error');
    e.target.value = '';
    return;
  }

  document.getElementById('fileNameDisplay').textContent = file.name;

  // Show preview
  const reader = new FileReader();
  reader.onload = function(event) {
    document.getElementById('sigPreviewImg').src = event.target.result;
    document.getElementById('sigPreviewContainer').classList.remove('hidden');
    document.getElementById('btnActivateMembership').disabled = false;
  };
  reader.readAsDataURL(file);
}

async function submitSignature() {
  const fileInput = document.getElementById('signatureFile');
  const file = fileInput.files[0];
  if (!file) {
    showToast('Please select a PNG signature file', 'error');
    return;
  }

  if (!state.currentMembershipUuid) {
    showToast('No active membership application context found', 'error');
    return;
  }

  const btn = document.getElementById('btnActivateMembership');
  btn.disabled = true;
  btn.textContent = 'Processing PDF & Activating...';

  const formData = new FormData();
  formData.append('file', file);

  const res = await fetchApi(`/memberships/${state.currentMembershipUuid}/sign`, {
    method: 'POST',
    body: formData
  });

  btn.textContent = 'Sign & Activate Membership';

  if (res.ok) {
    showToast('Membership activated successfully!', 'success');
    loadMembershipStatus();
  } else {
    btn.disabled = false;
    const errorMsg = res.data && res.data.message ? res.data.message : 'Failed to activate membership';
    showToast(errorMsg, 'error');
  }
}

async function downloadSignedPdf() {
  if (!state.currentMembershipId) {
    showToast('No membership ID found to download', 'error');
    return;
  }

  const cleanBase = state.baseUrl.replace(/\/+$/, '');
  const url = `${cleanBase}/memberships/${state.currentMembershipId}/agreement/pdf`;
  
  showToast('Initiating PDF download...', 'info');

  try {
    const response = await fetch(url, {
      headers: {
        'Authorization': `Bearer ${state.authToken}`
      }
    });

    if (!response.ok) {
      showToast('Failed to download PDF document', 'error');
      return;
    }

    const blob = await response.blob();
    const blobUrl = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = blobUrl;
    a.download = `${state.currentMembershipId}-signed-agreement.pdf`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(blobUrl);
    showToast('PDF agreement downloaded successfully', 'success');
  } catch (error) {
    console.error('Download error:', error);
    showToast('Error downloading signed PDF', 'error');
  }
}
