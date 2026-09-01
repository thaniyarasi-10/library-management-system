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

// DOM Content Loaded Handler
document.addEventListener('DOMContentLoaded', () => {
  initTheme();
  checkAuthSession();
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
function checkAuthSession() {
  const authScreen = document.getElementById('authScreen');
  const appShell = document.getElementById('appShell');

  if (state.authToken) {
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
  alertBox.classList.add('hidden');

  // Trigger Google OAuth authorization endpoint or simulation
  const googleOAuthUrl = `${state.baseUrl}/oauth2/authorization/google`;
  showToast('Redirecting to Google OAuth authentication...', 'info');

  // Fallback demo auth token login if local backend OAuth server is not configured
  setTimeout(() => {
    // Attempt OAuth redirect or set authenticated session
    window.location.href = googleOAuthUrl;
  }, 600);
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

    if (response.status === 401 || response.status === 403) {
      // Unauthenticated session
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
  state.currentPage = pageId;

  // Highlight Nav Links
  document.querySelectorAll('.nav-link').forEach(link => {
    if (link.getAttribute('data-page') === pageId) {
      link.classList.add('active');
    } else {
      link.classList.remove('active');
    }
  });

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
    dashboard: { title: 'Dashboard', sub: 'Overview of library holdings and staff operations' },
    books: { title: 'Book Catalog', sub: 'Manage library inventory, metadata, and book covers' },
    members: { title: 'Library Members', sub: 'View registered member profiles and patron records' },
    borrow: { title: 'Borrowing & Loans', sub: 'Issue new book loans and process book returns' },
    fines: { title: 'Fine Management', sub: 'Lookup member fine balances and process payments' },
    settings: { title: 'Settings', sub: 'Configure interface theme and application options' }
  };

  const info = titles[pageId] || { title: 'Athenaeum LMS', sub: 'Staff Portal' };
  document.getElementById('pageTitleDisplay').textContent = info.title;
  document.getElementById('pageSubtitleDisplay').textContent = info.sub;

  // Refresh page data
  if (pageId === 'dashboard') loadDashboardMetrics();
  if (pageId === 'books') loadBooks();
  if (pageId === 'members') loadMembers();
  if (pageId === 'borrow') populateBorrowDropdowns();
  if (pageId === 'fines') populateFineMemberDropdown();
}

async function loadInitialAppData() {
  loadBooks();
  loadMembers();
}

/* -------------------------------------------------------------------------- */
/* 5. DASHBOARD MODULE                                                        */
/* -------------------------------------------------------------------------- */
async function loadDashboardMetrics() {
  // Fetch total books count
  const resBooks = await fetchApi(`/books?page=0&size=1`);
  if (resBooks.ok && resBooks.data) {
    document.getElementById('dashTotalBooks').textContent = resBooks.data.totalElements || '0';
  }

  // Fetch total members count
  const resUsers = await fetchApi(`/user?page=0&size=1`);
  if (resUsers.ok && resUsers.data) {
    document.getElementById('dashTotalMembers').textContent = resUsers.data.totalElements || '0';
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

  tbody.innerHTML = state.books.map(b => `
    <tr>
      <td>
        ${renderBookThumbnailHTML(b)}
      </td>
      <td>
        <strong>${escapeHtml(b.title)}</strong>
        <br><span class="text-subtle" style="font-size:10px;">ID: #${b.id} &bull; ${b.uuid || ''}</span>
      </td>
      <td>${escapeHtml(b.author)}</td>
      <td><code>${escapeHtml(b.isbn)}</code></td>
      <td class="text-right">
        <div class="btn-group justify-end">
          <button class="btn btn-secondary btn-sm" onclick="viewBookDetails(${b.id})">View</button>
          <button class="btn btn-secondary btn-sm" onclick="openUploadCoverModal(${b.id})">Cover</button>
          <button class="btn btn-danger btn-sm" onclick="confirmDeleteBook(${b.id})">Delete</button>
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

// Cover image SVG fallback generator (No broken images)
function renderBookThumbnailHTML(book) {
  const monogram = getMonogram(book.title);
  return `<div class="table-thumb">${monogram}</div>`;
}

function renderBookCardHTML(book) {
  const monogram = getMonogram(book.title);
  return `
    <div class="book-card" onclick="viewBookDetails(${book.id})">
      <div class="book-cover-wrap">
        <div class="default-cover-placeholder">
          <div class="default-cover-monogram">${monogram}</div>
          <span style="font-size:10px; font-weight:600;">ATHENAEUM</span>
        </div>
      </div>
      <div class="book-card-body">
        <div class="book-card-title">${escapeHtml(book.title)}</div>
        <div class="book-card-author">${escapeHtml(book.author)}</div>
        <div class="book-card-isbn">ISBN: ${escapeHtml(book.isbn)}</div>
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
    showToast('Failed to load book details', 'error');
    return;
  }

  const b = res.data;
  const drawerBody = document.getElementById('bookDetailsBody');
  drawerBody.innerHTML = `
    <div class="book-cover-wrap mb-4" style="height:220px; border-radius:var(--radius-md);">
      <div class="default-cover-placeholder">
        <div class="default-cover-monogram" style="font-size:36px;">${getMonogram(b.title)}</div>
        <span style="font-size:12px; font-weight:700; color:var(--accent-primary);">ATHENAEUM CATALOG</span>
      </div>
    </div>

    <h2 style="font-size:20px; margin-bottom:4px;">${escapeHtml(b.title)}</h2>
    <p class="text-muted mb-4">By ${escapeHtml(b.author)}</p>

    <div class="form-group">
      <label class="form-label">ISBN Number</label>
      <div class="form-input" style="background:var(--bg-app);">${escapeHtml(b.isbn)}</div>
    </div>

    <div class="form-group">
      <label class="form-label">Database Record ID</label>
      <div class="form-input" style="background:var(--bg-app);">#${b.id} (${b.uuid || 'N/A'})</div>
    </div>

    <div class="btn-group mt-6">
      <button class="btn btn-secondary flex-1" onclick="openEditBookModal(${JSON.stringify(b).replace(/"/g, '&quot;')})">Edit Book</button>
      <button class="btn btn-secondary flex-1" onclick="openUploadCoverModal(${b.id})">Upload Cover</button>
    </div>
    <button class="btn btn-danger btn-block mt-4" onclick="confirmDeleteBook(${b.id})">Delete Book Title</button>
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
  document.getElementById('uploadCoverBookIdDisplay').textContent = `#${bookId}`;
  document.getElementById('coverPreviewContainer').classList.add('hidden');
  openModal('uploadCoverModal');
}

function handleCoverFileSelected(e) {
  const file = e.target.files[0];
  if (file) {
    const reader = new FileReader();
    reader.onload = function(evt) {
      document.getElementById('coverPreviewImg').src = evt.target.result;
      document.getElementById('coverPreviewContainer').classList.remove('hidden');
    };
    reader.readAsDataURL(file);
  }
}

async function handleUploadCoverSubmit(e) {
  e.preventDefault();
  const bookId = document.getElementById('uploadCoverBookId').value;
  const fileInput = document.getElementById('coverFileInput');

  if (!fileInput.files || fileInput.files.length === 0) {
    showToast('Please choose an image file', 'error');
    return;
  }

  const formData = new FormData();
  formData.append('file', fileInput.files[0]);

  const res = await fetchApi(`/books/${bookId}/cover`, {
    method: 'POST',
    body: formData
  });

  if (res.ok) {
    showToast('Cover image updated successfully', 'success');
    closeModal('uploadCoverModal');
    loadBooks();
  } else {
    showToast(`Cover upload failed (${res.status})`, 'error');
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
        <br><span class="text-subtle" style="font-size:10px;">${m.uuid || ''}</span>
      </td>
      <td>${escapeHtml(m.email)}</td>
      <td class="text-right">
        <div class="btn-group justify-end">
          <button class="btn btn-secondary btn-sm" onclick="viewMemberDetails(${m.id})">Profile</button>
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

async function viewMemberDetails(memberId) {
  const resUser = await fetchApi(`/user/${memberId}`);
  if (!resUser.ok) {
    showToast('Failed to load member profile', 'error');
    return;
  }

  const m = resUser.data;
  
  // Load member pending fines sum
  const resPendingTotal = await fetchApi(`/fines/user/${memberId}/pending-total`);
  let pendingAmount = 0;
  if (resPendingTotal.ok) {
    pendingAmount = typeof resPendingTotal.data === 'number' ? resPendingTotal.data : parseFloat(resPendingTotal.data) || 0;
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

    <div class="form-group">
      <label class="form-label">System UUID</label>
      <div class="form-input" style="background:var(--bg-app); font-size:11px;">${m.uuid || 'N/A'}</div>
    </div>

    <div class="btn-group mt-6">
      <button class="btn btn-secondary flex-1" onclick="navigateToMemberFines(${m.id})">View Member Fines</button>
      <button class="btn btn-danger btn-sm" onclick="confirmDeleteMember(${m.id})">Delete Member</button>
    </div>
  `;

  openDrawer('memberDetailsDrawer');
}

function navigateToMemberFines(memberId) {
  closeDrawer('memberDetailsDrawer');
  navigateTo('fines');
  setTimeout(() => {
    document.getElementById('fineMemberSelect').value = memberId;
    handleFineMemberChange(memberId);
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
/* 8. BORROWING & RETURNS MODULE                                             */
/* -------------------------------------------------------------------------- */
async function populateBorrowDropdowns() {
  const memberSelect = document.getElementById('borrowMemberSelect');
  const bookSelect = document.getElementById('borrowBookSelect');

  memberSelect.innerHTML = `<option value="">-- Choose Member --</option>`;
  bookSelect.innerHTML = `<option value="">-- Choose Book --</option>`;

  // Populate Members
  const resUsers = await fetchApi('/user?page=0&size=50');
  if (resUsers.ok && resUsers.data && resUsers.data.content) {
    resUsers.data.content.forEach(u => {
      memberSelect.innerHTML += `<option value="${u.id}">#${u.id} - ${escapeHtml(u.name || u.email)}</option>`;
    });
  }

  // Populate Books
  const resBooks = await fetchApi('/books?page=0&size=50');
  if (resBooks.ok && resBooks.data && resBooks.data.content) {
    resBooks.data.content.forEach(b => {
      bookSelect.innerHTML += `<option value="${b.id}">#${b.id} - ${escapeHtml(b.title)} (${escapeHtml(b.author)})</option>`;
    });
  }
}

async function handleIssueLoan(e) {
  e.preventDefault();
  const userId = parseInt(document.getElementById('borrowMemberSelect').value, 10);
  const bookId = parseInt(document.getElementById('borrowBookSelect').value, 10);

  if (!userId || !bookId) {
    showToast('Please select both a member and a book', 'error');
    return;
  }

  const res = await fetchApi('/borrow', {
    method: 'POST',
    body: JSON.stringify({ userId, bookId })
  });

  const display = document.getElementById('loanResultContent');
  if (res.ok && res.data) {
    const loan = res.data;
    showToast('Book loan issued successfully', 'success');
    display.innerHTML = `
      <div class="flex-between mb-4">
        <div>
          <span class="text-subtle" style="font-size:11px;">LOAN RECORD ID</span>
          <h4 style="font-size:18px;">#${loan.id || loan.borrowUuid || 'N/A'}</h4>
        </div>
        <span class="badge badge-warning">BORROWED</span>
      </div>
      <div class="grid-2col">
        <div>
          <span class="text-muted" style="font-size:11px;">Borrow Date</span>
          <div><strong>${loan.borrowDate || 'Today'}</strong></div>
        </div>
        <div>
          <span class="text-muted" style="font-size:11px;">Due Date</span>
          <div><strong class="text-danger">${loan.dueDate || 'In 14 Days'}</strong></div>
        </div>
      </div>
    `;
  } else {
    showToast(`Failed to issue loan (${res.status})`, 'error');
    display.innerHTML = `<p class="text-danger py-4">Error issuing loan (${res.status}): ${JSON.stringify(res.data)}</p>`;
  }
}

async function handleReturnBook(e) {
  e.preventDefault();
  const borrowId = parseInt(document.getElementById('returnBorrowId').value, 10);
  if (!borrowId) return;

  const res = await fetchApi(`/borrow/${borrowId}`, {
    method: 'PATCH'
  });

  const display = document.getElementById('loanResultContent');
  if (res.ok && res.data) {
    const loan = res.data;
    showToast('Book returned successfully', 'success');
    display.innerHTML = `
      <div class="flex-between mb-4">
        <div>
          <span class="text-subtle" style="font-size:11px;">RETURN PROCESSED</span>
          <h4 style="font-size:18px;">Loan Record #${borrowId}</h4>
        </div>
        <span class="badge badge-success">RETURNED</span>
      </div>
      <div>
        <span class="text-muted" style="font-size:11px;">Returned Date</span>
        <div><strong>${loan.returnedDate || 'Today'}</strong></div>
      </div>
    `;
  } else {
    showToast(`Return failed (${res.status})`, 'error');
    display.innerHTML = `<p class="text-danger py-4">Error returning book (${res.status}): ${JSON.stringify(res.data)}</p>`;
  }
}

/* -------------------------------------------------------------------------- */
/* 9. FINES MODULE                                                            */
/* -------------------------------------------------------------------------- */
async function populateFineMemberDropdown() {
  const select = document.getElementById('fineMemberSelect');
  select.innerHTML = `<option value="">-- Select Member --</option>`;

  const res = await fetchApi('/user?page=0&size=50');
  if (res.ok && res.data && res.data.content) {
    res.data.content.forEach(u => {
      select.innerHTML += `<option value="${u.id}">#${u.id} - ${escapeHtml(u.name || u.email)}</option>`;
    });
  }
}

async function handleFineMemberChange(memberId) {
  state.currentFineMemberId = memberId;
  if (!memberId) {
    document.getElementById('fineMemberName').textContent = 'None Selected';
    document.getElementById('fineTotalBalance').textContent = '$0.00';
    document.getElementById('finesTableBody').innerHTML = `<tr><td colspan="5" class="empty-cell">Select a member above to view fine history.</td></tr>`;
    return;
  }

  // Update Member Name Display
  const resUser = await fetchApi(`/user/${memberId}`);
  if (resUser.ok && resUser.data) {
    document.getElementById('fineMemberName').textContent = `#${memberId} - ${resUser.data.name || resUser.data.email}`;
  }

  refreshCurrentMemberFines();
}

async function refreshCurrentMemberFines() {
  const memberId = state.currentFineMemberId;
  if (!memberId) return;

  // Calculate Total Pending
  const resTotal = await fetchApi(`/fines/user/${memberId}/pending-total`);
  if (resTotal.ok) {
    const amt = typeof resTotal.data === 'number' ? resTotal.data : parseFloat(resTotal.data) || 0;
    document.getElementById('fineTotalBalance').textContent = `$${amt.toFixed(2)}`;
  }

  // Fetch Fines List
  const tbody = document.getElementById('finesTableBody');
  tbody.innerHTML = `<tr><td colspan="5" class="empty-cell">Loading fine records...</td></tr>`;

  const resFines = await fetchApi(`/fines/user/${memberId}`);
  if (!resFines.ok) {
    tbody.innerHTML = `<tr><td colspan="5" class="empty-cell text-danger">Error loading fines (${resFines.status})</td></tr>`;
    return;
  }

  const fines = Array.isArray(resFines.data) ? resFines.data : [];
  if (fines.length === 0) {
    tbody.innerHTML = `<tr><td colspan="5" class="empty-cell">No fine records registered for this member.</td></tr>`;
    return;
  }

  tbody.innerHTML = fines.map(f => `
    <tr>
      <td><strong>#${f.id}</strong></td>
      <td>Loan Record #${f.borrowId || f.borrowUuid || 'N/A'}</td>
      <td><strong>$${(f.amount || 0).toFixed(2)}</strong></td>
      <td>
        <span class="badge ${f.paid ? 'badge-success' : 'badge-danger'}">${f.paid ? 'PAID' : 'PENDING'}</span>
      </td>
      <td class="text-right">
        ${!f.paid ? `<button class="btn btn-primary btn-sm" onclick="confirmPayFine(${f.id})">Settle Payment</button>` : `<span class="text-muted" style="font-size:12px;">Settled</span>`}
      </td>
    </tr>
  `).join('');
}

function confirmPayFine(fineId) {
  showConfirmModal('Settle Fine Payment', `Are you sure you want to mark Fine #${fineId} as PAID?`, async () => {
    const res = await fetchApi(`/fines/${fineId}/pay`, { method: 'POST' });
    if (res.ok) {
      showToast(`Fine #${fineId} payment settled`, 'success');
      refreshCurrentMemberFines();
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
