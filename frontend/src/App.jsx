import React, { useState, useEffect } from 'react';
import {
  BookOpen, Users, LayoutDashboard, CreditCard, Award, Sun, Moon,
  Plus, Search, LogOut, X, ChevronDown, Check, Shield, FileText, Upload, RefreshCw
} from 'lucide-react';
import './styles.css';

export default function App() {
  // Theme state
  const [theme, setTheme] = useState(localStorage.getItem('athenaeum_theme') || 'dark');

  // Auth state
  const [authToken, setAuthToken] = useState(localStorage.getItem('athenaeum_token') || '');
  const [currentUser, setCurrentUser] = useState(null);
  const [userRole, setUserRole] = useState('');
  const [authError, setAuthError] = useState('');
  const [authLoading, setAuthLoading] = useState(false);
  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showUserDropdown, setShowUserDropdown] = useState(false);

  // Navigation
  const [currentPage, setCurrentPage] = useState('dashboard');
  const [globalSearch, setGlobalSearch] = useState('');

  // Data states
  const [dashboardStats, setDashboardStats] = useState({ books: 0, members: 0 });
  const [recentBooks, setRecentBooks] = useState([]);

  // Books page
  const [books, setBooks] = useState([]);
  const [booksPage, setBooksPage] = useState(0);
  const [booksTotalPages, setBooksTotalPages] = useState(1);
  const [bookSearchQuery, setBookSearchQuery] = useState('');

  // Members page
  const [members, setMembers] = useState([]);
  const [membersPage, setMembersPage] = useState(0);
  const [membersTotalPages, setMembersTotalPages] = useState(1);
  const [memberSearchQuery, setMemberSearchQuery] = useState('');

  // Borrows
  const [adminBorrows, setAdminBorrows] = useState([]);
  const [userBorrows, setUserBorrows] = useState([]);

  // Fines
  const [fines, setFines] = useState([]);
  const [allMembersForFines, setAllMembersForFines] = useState([]);
  const [selectedFineMemberId, setSelectedFineMemberId] = useState('');
  const [fineTotalBalance, setFineTotalBalance] = useState(0);

  // Membership
  const [membership, setMembership] = useState(null);
  const [agreementText, setAgreementText] = useState('');
  const [sigFile, setSigFile] = useState(null);
  const [sigPreviewUrl, setSigPreviewUrl] = useState('');

  // Modals & Drawers
  const [activeModal, setActiveModal] = useState(null); // 'createBook', 'editBook', 'uploadCover', 'createUser', 'editUser', 'adminBorrow', 'userBorrow', 'googleToken', 'confirm'
  const [drawerData, setDrawerData] = useState(null); // { type: 'book'|'member', data: obj }
  const [confirmConfig, setConfirmConfig] = useState({ title: '', message: '', actionBtnText: 'Confirm', onConfirm: null });

  // Form models
  const [bookForm, setBookForm] = useState({ id: '', title: '', author: '', isbn: '' });
  const [userForm, setUserForm] = useState({ id: '', name: '', email: '', password: '' });
  const [selectedBookForCover, setSelectedBookForCover] = useState({ id: '', title: '' });
  const [coverFile, setCoverFile] = useState(null);
  const [coverPreview, setCoverPreview] = useState('');
  const [adminBorrowSelect, setAdminBorrowSelect] = useState({ memberId: '', bookId: '' });
  const [userBorrowBookId, setUserBorrowBookId] = useState('');
  const [manualOAuthToken, setManualOAuthToken] = useState('');

  // Toasts
  const [toasts, setToasts] = useState([]);

  const baseUrl = 'http://localhost:8080';

  const showToast = (message, type = 'info') => {
    const id = Date.now();
    setToasts(prev => [...prev, { id, message, type }]);
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id));
    }, 4000);
  };

  // Helper JWT Decoder
  const parseJwt = (token) => {
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return null;
      const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(atob(base64).split('').map(c => {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
      }).join(''));
      return JSON.parse(jsonPayload);
    } catch (e) {
      return null;
    }
  };

  const fetchApi = async (endpoint, options = {}) => {
    const headers = {
      ...(options.headers || {})
    };
    if (authToken && !headers['Authorization']) {
      headers['Authorization'] = `Bearer ${authToken}`;
    }
    if (!(options.body instanceof FormData) && !headers['Content-Type']) {
      headers['Content-Type'] = 'application/json';
    }

    try {
      const res = await fetch(`${baseUrl}${endpoint}`, { ...options, headers });
      if (res.status === 401) {
        handleSignOut();
        showToast('Session expired, please sign in again', 'error');
        return { ok: false, status: 401 };
      }

      const contentType = res.headers.get('content-type');
      let data = null;
      if (contentType && contentType.includes('application/json')) {
        data = await res.json();
      } else if (contentType && (contentType.includes('application/pdf') || contentType.includes('image/'))) {
        data = await res.blob();
      } else {
        data = await res.text();
      }

      return { ok: res.ok, status: res.status, data };
    } catch (err) {
      console.error('API Call Error:', err);
      return { ok: false, status: 0, error: err };
    }
  };

  // Check URL token parameter on mount (fallback redirect from OAuth)
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const tokenParam = params.get('token');
    if (tokenParam) {
      setAuthToken(tokenParam);
      localStorage.setItem('athenaeum_token', tokenParam);
      // Clean query string from browser bar
      window.history.replaceState({}, document.title, window.location.pathname);
      showToast('Signed in successfully', 'success');
    }
  }, []);

  // Theme Sync
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('athenaeum_theme', theme);
  }, [theme]);

  // Auth Initialization
  useEffect(() => {
    if (!authToken) return;
    localStorage.setItem('athenaeum_token', authToken);

    const jwtPayload = parseJwt(authToken);
    if (!jwtPayload) {
      handleSignOut();
      return;
    }

    let role = 'USER';
    if (jwtPayload.role) {
      role = jwtPayload.role.replace('ROLE_', '');
    } else if (jwtPayload.roles && jwtPayload.roles.length > 0) {
      role = jwtPayload.roles[0].replace('ROLE_', '');
    }
    setUserRole(role);

    // Load user profile
    fetchApi('/user/me').then(res => {
      if (res.ok && res.data) {
        setCurrentUser(res.data);
      } else {
        setCurrentUser({ email: jwtPayload.sub || jwtPayload.username || 'User', name: jwtPayload.name || 'User' });
      }
    });
  }, [authToken]);

  // Load Page Data on Change
  useEffect(() => {
    if (!authToken) return;
    if (currentPage === 'dashboard') {
      loadDashboard();
    } else if (currentPage === 'books') {
      loadBooks(booksPage, bookSearchQuery);
    } else if (currentPage === 'members') {
      if (userRole === 'ADMIN') loadMembers(membersPage, memberSearchQuery);
    } else if (currentPage === 'borrow') {
      loadBorrows();
    } else if (currentPage === 'fines') {
      loadFines();
    } else if (currentPage === 'membership') {
      loadMembership();
    }
  }, [currentPage, authToken, userRole, booksPage, membersPage]);

  // Listen for postMessage from Google OAuth popup
  useEffect(() => {
    const handleMessage = (event) => {
      if (event.data && event.data.type === 'ATHENAEUM_OAUTH_TOKEN' && event.data.token) {
        setAuthToken(event.data.token);
        showToast('Signed in with Google successfully', 'success');
        setActiveModal(null);
      }
    };
    window.addEventListener('message', handleMessage);
    return () => window.removeEventListener('message', handleMessage);
  }, []);

  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    setAuthError('');
    setAuthLoading(true);
    const res = await fetchApi('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email: loginEmail, password: loginPassword })
    });
    setAuthLoading(false);

    if (res.ok && res.data && res.data.token) {
      setAuthToken(res.data.token);
      showToast('Successfully signed in', 'success');
    } else {
      setAuthError(res.data?.message || 'Invalid email address or password.');
    }
  };

  const handleGoogleSignIn = () => {
    const popup = window.open(`${baseUrl}/oauth2/authorization/google`, 'googleOAuth', 'width=500,height=600');
    setTimeout(() => {
      if (popup && !popup.closed) {
        setActiveModal('googleToken');
      }
    }, 2500);
  };

  const handleSignOut = () => {
    setAuthToken('');
    localStorage.removeItem('athenaeum_token');
    setCurrentUser(null);
    setUserRole('');
    setCurrentPage('dashboard');
  };

  // Monogram Helper
  const getMonogram = (name) => {
    if (!name) return 'U';
    const parts = name.trim().split(' ');
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
    return name.substring(0, 2).toUpperCase();
  };

  // Helper for Cover Image URL
  const getCoverUrl = (url) => {
    if (!url) return null;
    if (url.startsWith('http://') || url.startsWith('https://')) return url;
    return `${baseUrl}${url.startsWith('/') ? '' : '/'}${url}`;
  };

  // Dashboard Loader
  const loadDashboard = async () => {
    const resBooks = await fetchApi('/books?page=0&size=4');
    if (resBooks.ok && resBooks.data) {
      const bList = resBooks.data.content || resBooks.data || [];
      setRecentBooks(bList);
      setDashboardStats(prev => ({ ...prev, books: resBooks.data.totalElements || bList.length }));
    }

    if (userRole === 'ADMIN') {
      const resMembers = await fetchApi('/user?page=0&size=1');
      if (resMembers.ok && resMembers.data) {
        setDashboardStats(prev => ({ ...prev, members: resMembers.data.totalElements || 0 }));
      }
    } else {
      loadMembership();
      loadUserFinesTotal();
    }
  };

  const loadUserFinesTotal = async () => {
    const res = await fetchApi('/fines/me');
    if (res.ok && res.data) {
      const fList = Array.isArray(res.data) ? res.data : [];
      const total = fList.reduce((acc, f) => acc + (f.status === 'UNPAID' ? (f.amount || 0) : 0), 0);
      setFineTotalBalance(total);
    }
  };

  // Books Loader
  const loadBooks = async (page = 0, query = '') => {
    let url = `/books?page=${page}&size=10`;
    if (query) {
      url = `/books/search?query=${encodeURIComponent(query)}&page=${page}&size=10`;
    }
    const res = await fetchApi(url);
    if (res.ok && res.data) {
      const content = res.data.content || res.data || [];
      setBooks(content);
      setBooksTotalPages(res.data.totalPages || 1);
    }
  };

  const handleBookSearch = (e) => {
    e.preventDefault();
    setBooksPage(0);
    loadBooks(0, bookSearchQuery);
  };

  const handleCreateBookSubmit = async (e) => {
    e.preventDefault();
    const res = await fetchApi('/books', {
      method: 'POST',
      body: JSON.stringify({ title: bookForm.title, author: bookForm.author, isbn: bookForm.isbn })
    });
    if (res.ok) {
      showToast('Book created successfully', 'success');
      setActiveModal(null);
      setBookForm({ id: '', title: '', author: '', isbn: '' });
      loadBooks(booksPage, bookSearchQuery);
    } else {
      showToast(res.data?.message || 'Failed to create book', 'error');
    }
  };

  const handleEditBookSubmit = async (e) => {
    e.preventDefault();
    const res = await fetchApi(`/books/${bookForm.id}`, {
      method: 'PUT',
      body: JSON.stringify({ title: bookForm.title, author: bookForm.author, isbn: bookForm.isbn })
    });
    if (res.ok) {
      showToast('Book updated successfully', 'success');
      setActiveModal(null);
      loadBooks(booksPage, bookSearchQuery);
    } else {
      showToast(res.data?.message || 'Failed to update book', 'error');
    }
  };

  const handleDeleteBook = (bookId, title) => {
    setConfirmConfig({
      title: 'Delete Book',
      message: `Are you sure you want to delete "${title}"?`,
      actionBtnText: 'Delete Book',
      onConfirm: async () => {
        const res = await fetchApi(`/books/${bookId}`, { method: 'DELETE' });
        if (res.ok) {
          showToast('Book deleted', 'success');
          loadBooks(booksPage, bookSearchQuery);
        } else {
          showToast(res.data?.message || 'Failed to delete book', 'error');
        }
      }
    });
    setActiveModal('confirm');
  };

  const handleUploadCoverSubmit = async (e) => {
    e.preventDefault();
    if (!coverFile) {
      showToast('Please select an image file', 'error');
      return;
    }
    const formData = new FormData();
    formData.append('file', coverFile);
    const res = await fetchApi(`/books/${selectedBookForCover.id}/cover`, {
      method: 'POST',
      body: formData
    });
    if (res.ok) {
      showToast('Cover image updated', 'success');
      setActiveModal(null);
      setCoverFile(null);
      setCoverPreview('');
      loadBooks(booksPage, bookSearchQuery);
    } else {
      showToast(res.data?.message || 'Failed to upload cover', 'error');
    }
  };

  // Members Loader
  const loadMembers = async (page = 0, query = '') => {
    let url = `/user?page=${page}&size=10`;
    if (query) url = `/user/search?query=${encodeURIComponent(query)}&page=${page}&size=10`;
    const res = await fetchApi(url);
    if (res.ok && res.data) {
      setMembers(res.data.content || res.data || []);
      setMembersTotalPages(res.data.totalPages || 1);
    }
  };

  const handleCreateUserSubmit = async (e) => {
    e.preventDefault();
    const res = await fetchApi('/user', {
      method: 'POST',
      body: JSON.stringify(userForm)
    });
    if (res.ok) {
      showToast('Member registered successfully', 'success');
      setActiveModal(null);
      setUserForm({ id: '', name: '', email: '', password: '' });
      loadMembers(membersPage, memberSearchQuery);
    } else {
      showToast(res.data?.message || 'Failed to register member', 'error');
    }
  };

  const handleEditUserSubmit = async (e) => {
    e.preventDefault();
    const payload = { name: userForm.name, email: userForm.email };
    if (userForm.password) payload.password = userForm.password;
    const res = await fetchApi(`/user/${userForm.id}`, {
      method: 'PUT',
      body: JSON.stringify(payload)
    });
    if (res.ok) {
      showToast('Member updated', 'success');
      setActiveModal(null);
      loadMembers(membersPage, memberSearchQuery);
    } else {
      showToast(res.data?.message || 'Failed to update member', 'error');
    }
  };

  const handleDeleteUser = (userId, name) => {
    setConfirmConfig({
      title: 'Delete Member',
      message: `Are you sure you want to delete member profile "${name}"?`,
      actionBtnText: 'Delete Member',
      onConfirm: async () => {
        const res = await fetchApi(`/user/${userId}`, { method: 'DELETE' });
        if (res.ok) {
          showToast('Member deleted', 'success');
          loadMembers(membersPage, memberSearchQuery);
        } else {
          showToast(res.data?.message || 'Failed to delete member', 'error');
        }
      }
    });
    setActiveModal('confirm');
  };

  // Borrows Loader
  const loadBorrows = async () => {
    if (userRole === 'ADMIN') {
      const res = await fetchApi('/borrow');
      if (res.ok && res.data) {
        setAdminBorrows(res.data);
      }
      // Populate selects for modal
      const resM = await fetchApi('/user?page=0&size=100');
      if (resM.ok && resM.data) setAllMembersForFines(resM.data.content || resM.data || []);
      const resB = await fetchApi('/books?page=0&size=100');
      if (resB.ok && resB.data) setBooks(resB.data.content || resB.data || []);
    } else {
      const res = await fetchApi('/borrow/me');
      if (res.ok && res.data) {
        setUserBorrows(res.data);
      }
      const resB = await fetchApi('/books?page=0&size=100');
      if (resB.ok && resB.data) setBooks(resB.data.content || resB.data || []);
    }
  };

  const handleAdminIssueLoan = async (e) => {
    e.preventDefault();
    const res = await fetchApi('/borrow', {
      method: 'POST',
      body: JSON.stringify({ userId: adminBorrowSelect.memberId, bookId: adminBorrowSelect.bookId })
    });
    if (res.ok) {
      showToast('Book borrow issued', 'success');
      setActiveModal(null);
      loadBorrows();
    } else {
      showToast(res.data?.message || 'Failed to issue borrow', 'error');
    }
  };

  const handleUserSelfBorrow = async (e) => {
    e.preventDefault();
    if (!currentUser || !currentUser.id) return;

    // Check membership
    const resMem = await fetchApi('/memberships/me');
    if (!resMem.ok || !resMem.data || resMem.data.status !== 'ACTIVE') {
      showToast('Active membership required to check out books', 'error');
      setCurrentPage('membership');
      setActiveModal(null);
      return;
    }

    const res = await fetchApi('/borrow', {
      method: 'POST',
      body: JSON.stringify({ userId: currentUser.id, bookId: userBorrowBookId })
    });
    if (res.ok) {
      showToast('Book checked out successfully!', 'success');
      setActiveModal(null);
      loadBorrows();
    } else {
      showToast(res.data?.message || 'Failed to borrow book', 'error');
    }
  };

  const handleReturnBook = async (borrowId) => {
    const res = await fetchApi(`/borrow/${borrowId}`, { method: 'PATCH' });
    if (res.ok) {
      showToast('Book returned successfully', 'success');
      loadBorrows();
    } else {
      showToast(res.data?.message || 'Failed to return book', 'error');
    }
  };

  // Fines Loader
  const loadFines = async () => {
    if (userRole === 'ADMIN') {
      const resM = await fetchApi('/user?page=0&size=100');
      if (resM.ok && resM.data) setAllMembersForFines(resM.data.content || resM.data || []);

      let url = '/fines';
      if (selectedFineMemberId) url = `/fines/user/${selectedFineMemberId}`;
      const res = await fetchApi(url);
      if (res.ok && res.data) {
        const fList = Array.isArray(res.data) ? res.data : [];
        setFines(fList);
        const total = fList.reduce((acc, f) => acc + (f.status === 'UNPAID' ? (f.amount || 0) : 0), 0);
        setFineTotalBalance(total);
      }
    } else {
      const res = await fetchApi('/fines/me');
      if (res.ok && res.data) {
        const fList = Array.isArray(res.data) ? res.data : [];
        setFines(fList);
        const total = fList.reduce((acc, f) => acc + (f.status === 'UNPAID' ? (f.amount || 0) : 0), 0);
        setFineTotalBalance(total);
      }
    }
  };

  const handlePayFine = async (fineId) => {
    const res = await fetchApi(`/fines/${fineId}/pay`, { method: 'POST' });
    if (res.ok) {
      showToast('Fine payment processed successfully', 'success');
      loadFines();
    } else {
      showToast(res.data?.message || 'Failed to settle fine', 'error');
    }
  };

  // Membership Loader
  const loadMembership = async () => {
    const res = await fetchApi('/memberships/me');
    if (res.ok && res.data) {
      setMembership(res.data);
      const memUuid = res.data.membershipUuid || res.data.id;
      if (memUuid) {
        const resTerms = await fetchApi(`/memberships/${memUuid}/agreement`);
        if (resTerms.ok && resTerms.data) {
          setAgreementText(typeof resTerms.data === 'string' ? resTerms.data : resTerms.data.terms || 'Athenaeum Library Membership Agreement...');
        }
      }
    } else {
      setMembership(null);
    }
  };

  const handleApplyMembership = async () => {
    const res = await fetchApi('/memberships', { method: 'POST' });
    if (res.ok && res.data) {
      setMembership(res.data);
      showToast('Membership application created. Please review and sign terms.', 'info');
      loadMembership();
    } else {
      showToast(res.data?.message || 'Failed to apply for membership', 'error');
    }
  };

  const handleSignatureSubmit = async () => {
    if (!sigFile || !membership || !membership.membershipUuid) {
      showToast('Please select a signature PNG image', 'error');
      return;
    }
    const formData = new FormData();
    formData.append('file', sigFile);
    const res = await fetchApi(`/memberships/${membership.membershipUuid}/sign`, {
      method: 'POST',
      body: formData
    });
    if (res.ok && res.data) {
      setMembership(res.data);
      showToast('Membership signed & activated successfully!', 'success');
      setSigFile(null);
      setSigPreviewUrl('');
    } else {
      showToast(res.data?.message || 'Failed to activate membership', 'error');
    }
  };

  const handleDownloadPdf = async () => {
    const memId = membership?.membershipId || membership?.id;
    if (!membership || !memId) {
      showToast('Membership ID not found', 'error');
      return;
    }
    const res = await fetchApi(`/memberships/${memId}/agreement/pdf`);
    if (res.ok && res.data instanceof Blob) {
      const blobUrl = URL.createObjectURL(res.data);
      const link = document.createElement('a');
      link.href = blobUrl;
      link.download = `${memId}-signed-agreement.pdf`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(blobUrl);
    } else {
      showToast(res.data?.message || 'Failed to download PDF agreement', 'error');
    }
  };

  const handleCancelMembership = () => {
    setConfirmConfig({
      title: 'Cancel Library Membership',
      message: 'Are you sure you want to cancel your membership? You will no longer be able to borrow new books.',
      actionBtnText: 'Cancel Membership',
      onConfirm: async () => {
        const res = await fetchApi('/memberships/cancel', { method: 'POST' });
        if (res.ok) {
          showToast('Membership cancelled successfully', 'success');
          loadMembership();
        } else {
          showToast(res.data?.message || 'Failed to cancel membership', 'error');
        }
      }
    });
    setActiveModal('confirm');
  };

  // Render Login Screen if not authenticated
  if (!authToken) {
    return (
      <div className="auth-screen">
        <div className="auth-card">
          <div className="auth-brand">
            <div className="brand-icon">
              <BookOpen size={28} />
            </div>
            <h1 className="brand-title">Padips</h1>
            <p className="brand-subtitle">Library Management System</p>
          </div>

          <div className="auth-header">
            <h2>Sign in to portal</h2>
            <p>Enter your credentials to access the library dashboard</p>
          </div>

          {authError && <div className="alert alert-danger">{authError}</div>}

          <button type="button" className="btn btn-google btn-block btn-lg mb-4" onClick={handleGoogleSignIn}>
            <svg className="google-icon" width="18" height="18" viewBox="0 0 24 24">
              <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
              <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
              <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" />
              <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" />
            </svg>
            <span>Sign in with Google</span>
          </button>

          <div className="auth-divider mb-4">
            <span>OR</span>
          </div>

          <form onSubmit={handleLoginSubmit}>
            <div className="form-group">
              <label htmlFor="loginEmail" className="form-label">Email address</label>
              <div className="input-wrapper">
                <input
                  type="email"
                  id="loginEmail"
                  className="form-input"
                  placeholder="staff@athenaeum.org"
                  required
                  value={loginEmail}
                  onChange={(e) => setLoginEmail(e.target.value)}
                />
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="loginPassword" className="form-label">Password</label>
              <div className="input-wrapper password-input-wrapper">
                <input
                  type={showPassword ? 'text' : 'password'}
                  id="loginPassword"
                  className="form-input"
                  placeholder="••••••••"
                  required
                  value={loginPassword}
                  onChange={(e) => setLoginPassword(e.target.value)}
                />
                <button type="button" className="btn-toggle-password" onClick={() => setShowPassword(!showPassword)}>
                  <Shield size={18} />
                </button>
              </div>
            </div>

            <button type="submit" className="btn btn-primary btn-block btn-lg" disabled={authLoading}>
              <span className="btn-text">{authLoading ? 'Signing in...' : 'Sign in'}</span>
            </button>
          </form>

          <div className="auth-footer">
            <span>Library Portal</span>
          </div>
        </div>
      </div>
    );
  }

  // Production App Shell
  const userName = currentUser ? (currentUser.name || currentUser.email) : (userRole === 'ADMIN' ? 'Library Staff' : 'Library Member');
  const userEmail = currentUser ? currentUser.email : '';
  const monogram = getMonogram(userName);

  return (
    <div className="app-shell">
      {/* SIDEBAR */}
      <aside className="sidebar">
        <div className="sidebar-brand">
          <div className="brand-emblem">
            <BookOpen size={20} />
          </div>
          <div className="brand-text">
            <span className="brand-name">Padips</span>
            <span className="brand-tag">{userRole === 'ADMIN' ? 'ADMIN PORTAL' : 'MEMBER HUB'}</span>
          </div>
        </div>

        <nav className="sidebar-nav">
          <div className="nav-group-label">NAVIGATION</div>

          <button
            className={`nav-link ${currentPage === 'dashboard' ? 'active' : ''}`}
            onClick={() => setCurrentPage('dashboard')}
          >
            <LayoutDashboard className="nav-icon" size={18} />
            <span>Dashboard</span>
          </button>

          <button
            className={`nav-link ${currentPage === 'books' ? 'active' : ''}`}
            onClick={() => setCurrentPage('books')}
          >
            <BookOpen className="nav-icon" size={18} />
            <span>{userRole === 'ADMIN' ? 'Books Catalog' : 'Browse Catalog'}</span>
          </button>

          {userRole === 'ADMIN' && (
            <button
              className={`nav-link ${currentPage === 'members' ? 'active' : ''}`}
              onClick={() => setCurrentPage('members')}
            >
              <Users className="nav-icon" size={18} />
              <span>Members</span>
            </button>
          )}

          <button
            className={`nav-link ${currentPage === 'borrow' ? 'active' : ''}`}
            onClick={() => setCurrentPage('borrow')}
          >
            <FileText className="nav-icon" size={18} />
            <span>{userRole === 'ADMIN' ? 'Borrowed Books' : 'Borrow & Return'}</span>
          </button>

          <button
            className={`nav-link ${currentPage === 'fines' ? 'active' : ''}`}
            onClick={() => setCurrentPage('fines')}
          >
            <CreditCard className="nav-icon" size={18} />
            <span>{userRole === 'ADMIN' ? 'Fine Management' : 'My Fines & Dues'}</span>
          </button>

          <button
            className={`nav-link ${currentPage === 'membership' ? 'active' : ''}`}
            onClick={() => setCurrentPage('membership')}
          >
            <Award className="nav-icon" size={18} />
            <span>{userRole === 'ADMIN' ? 'Membership' : 'Digital Membership'}</span>
          </button>
        </nav>

        <div className="sidebar-profile">
          <div className="user-avatar">{monogram}</div>
          <div className="user-details">
            <span className="user-name">{userName}</span>
            <span className="user-role">{userRole === 'ADMIN' ? 'Librarian (Admin)' : 'Member'}</span>
          </div>
          <button className="btn-signout-icon" onClick={handleSignOut} title="Sign Out">
            <LogOut size={18} />
          </button>
        </div>
      </aside>

      {/* MAIN WRAPPER */}
      <div className="main-wrapper">
        <header className="top-header">
          <div className="header-titles">
            <h1 className="header-page-title">
              {currentPage === 'dashboard' && 'Dashboard'}
              {currentPage === 'books' && (userRole === 'ADMIN' ? 'Books Catalog' : 'Browse Catalog')}
              {currentPage === 'members' && 'Members Directory'}
              {currentPage === 'borrow' && (userRole === 'ADMIN' ? 'Circulation Log' : 'My Loans')}
              {currentPage === 'fines' && (userRole === 'ADMIN' ? 'Fine Settlement' : 'Fines & Dues')}
              {currentPage === 'membership' && 'Digital Membership'}
            </h1>
            <p className="header-page-subtitle">Overview of library holdings and staff operations</p>
          </div>

          <div className="header-actions">
            <button className="theme-toggle-btn" title="Toggle Light / Dark Mode" onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}>
              {theme === 'dark' ? <Sun size={18} className="icon-sun" /> : <Moon size={18} className="icon-moon" />}
            </button>

            <div className="user-menu-wrapper">
              <button className="user-menu-btn" onClick={() => setShowUserDropdown(!showUserDropdown)}>
                <div className="avatar-sm">{monogram}</div>
                <span className="user-display-name">{userName}</span>
                <ChevronDown size={14} />
              </button>
              {showUserDropdown && (
                <div className="user-dropdown-menu">
                  <div className="dropdown-header">
                    <span className="dropdown-user-name">{userName}</span>
                    <span className="dropdown-user-email">{userEmail}</span>
                  </div>
                  <div className="dropdown-divider"></div>
                  <button className="dropdown-item danger" onClick={handleSignOut}>Sign Out</button>
                </div>
              )}
            </div>
          </div>
        </header>

        {/* MAIN CONTENT AREA */}
        <main className="content-area">
          {/* PAGE 1: DASHBOARD */}
          {currentPage === 'dashboard' && (
            <div className="page-pane active">
              {userRole === 'ADMIN' ? (
                <div>
                  <div className="metrics-grid">
                    <div className="metric-card" onClick={() => setCurrentPage('books')} style={{ cursor: 'pointer' }}>
                      <div className="metric-icon icon-books">
                        <BookOpen size={22} />
                      </div>
                      <div className="metric-data">
                        <span className="metric-label">Total Books</span>
                        <span className="metric-value">{dashboardStats.books}</span>
                      </div>
                    </div>

                    <div className="metric-card" onClick={() => setCurrentPage('members')} style={{ cursor: 'pointer' }}>
                      <div className="metric-icon icon-members">
                        <Users size={22} />
                      </div>
                      <div className="metric-data">
                        <span className="metric-label">Registered Members</span>
                        <span className="metric-value">{dashboardStats.members}</span>
                      </div>
                    </div>
                  </div>

                  <div className="section-card mt-6">
                    <div className="section-header">
                      <h3>Staff Quick Actions</h3>
                      <p className="section-desc">Frequently performed librarian tasks</p>
                    </div>
                    <div className="quick-actions-row">
                      <button className="quick-action-btn" onClick={() => { setBookForm({ id: '', title: '', author: '', isbn: '' }); setActiveModal('createBook'); }}>
                        <div className="qa-icon">+</div>
                        <div className="qa-text">
                          <span className="qa-title">Add New Book</span>
                          <span className="qa-desc">Add title to inventory</span>
                        </div>
                      </button>
                      <button className="quick-action-btn" onClick={() => { setUserForm({ id: '', name: '', email: '', password: '' }); setActiveModal('createUser'); }}>
                        <div className="qa-icon">+</div>
                        <div className="qa-text">
                          <span className="qa-title">Register Member</span>
                          <span className="qa-desc">Create new patron profile</span>
                        </div>
                      </button>
                    </div>
                  </div>
                </div>
              ) : (
                <div>
                  <div className="user-welcome-banner mb-6" style={{ background: 'linear-gradient(135deg, rgba(224, 122, 73, 0.12) 0%, rgba(224, 122, 73, 0.02) 100%)', border: '1px solid var(--border-medium)', borderRadius: 'var(--radius-lg)', padding: '24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
                    <div>
                      <span className="badge badge-primary mb-2" style={{ background: 'var(--accent-subtle)', color: 'var(--accent-primary)', fontWeight: 600, fontSize: '11px', padding: '4px 10px', borderRadius: '999px' }}>LIBRARY MEMBER PORTAL</span>
                      <h2 style={{ fontSize: '22px', fontWeight: 700, marginTop: '6px', marginBottom: '4px' }}>Welcome to Athenaeum</h2>
                      <p className="text-muted" style={{ fontSize: '14px' }}>Browse books, borrow titles with your digital membership, and manage loan returns.</p>
                    </div>
                    <div>
                      <button className="btn btn-primary" onClick={() => setCurrentPage('books')}>Browse Books Catalog &rarr;</button>
                    </div>
                  </div>

                  <div className="metrics-grid">
                    <div className="metric-card" onClick={() => setCurrentPage('books')} style={{ cursor: 'pointer' }}>
                      <div className="metric-icon icon-books">
                        <BookOpen size={22} />
                      </div>
                      <div className="metric-data">
                        <span className="metric-label">Catalog Titles</span>
                        <span className="metric-value">{dashboardStats.books}</span>
                      </div>
                    </div>

                    <div className="metric-card" onClick={() => setCurrentPage('fines')} style={{ cursor: 'pointer' }}>
                      <div className="metric-icon" style={{ background: 'rgba(239, 68, 68, 0.15)', color: '#F87171' }}>
                        <CreditCard size={22} />
                      </div>
                      <div className="metric-data">
                        <span className="metric-label">My Outstanding Fines</span>
                        <span className="metric-value text-danger">${fineTotalBalance.toFixed(2)}</span>
                      </div>
                    </div>

                    <div className="metric-card" onClick={() => setCurrentPage('membership')} style={{ cursor: 'pointer' }}>
                      <div className="metric-icon" style={{ background: 'rgba(16, 185, 129, 0.15)', color: '#34D399' }}>
                        <Award size={22} />
                      </div>
                      <div className="metric-data">
                        <span className="metric-label">Membership Status</span>
                        <span className="metric-value">{membership ? membership.status : 'NOT APPLIED'}</span>
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* Showcase Grid */}
              <div className="section-card mt-6">
                <div className="section-header flex-between">
                  <div>
                    <h3>Catalog Showcase</h3>
                    <p className="section-desc">Recently updated titles in library catalog</p>
                  </div>
                  <button className="btn btn-secondary btn-sm" onClick={() => setCurrentPage('books')}>View All Catalog &rarr;</button>
                </div>

                <div className="book-card-grid mt-4">
                  {recentBooks.map((b) => (
                    <div className="book-card" key={b.id}>
                      <div className="book-cover-wrap">
                        {b.coverImageUrl ? (
                          <img src={getCoverUrl(b.coverImageUrl)} alt={b.title} className="book-cover-img" />
                        ) : (
                          <div className="default-cover-placeholder">
                            <span className="default-cover-monogram">{getMonogram(b.title)}</span>
                          </div>
                        )}
                      </div>
                      <div className="book-card-body">
                        <span className="book-card-title">{b.title}</span>
                        <span className="book-card-author">by {b.author}</span>
                        <span className="book-card-isbn">ISBN: {b.isbn}</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* PAGE 2: BOOKS CATALOG */}
          {currentPage === 'books' && (
            <div className="page-pane active">
              <div className="action-bar">
                <form className="filter-controls" onSubmit={handleBookSearch}>
                  <div className="search-input-group">
                    <Search size={16} />
                    <input
                      type="text"
                      placeholder="Search by title, author, or ISBN..."
                      value={bookSearchQuery}
                      onChange={(e) => setBookSearchQuery(e.target.value)}
                    />
                  </div>
                  <button type="submit" className="btn btn-secondary">Search</button>
                  <button type="button" className="btn btn-ghost" onClick={() => { setBookSearchQuery(''); loadBooks(0, ''); }}>Clear</button>
                </form>

                {userRole === 'ADMIN' && (
                  <button className="btn btn-primary" onClick={() => { setBookForm({ id: '', title: '', author: '', isbn: '' }); setActiveModal('createBook'); }}>
                    <Plus size={16} />
                    <span>Add Book</span>
                  </button>
                )}
              </div>

              <div className="table-container mt-4">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th width="70">Cover</th>
                      <th>Title & Metadata</th>
                      <th>Author</th>
                      <th>ISBN</th>
                      <th width="180" className="text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {books.length === 0 ? (
                      <tr><td colSpan="5" className="empty-cell">No books found in catalog.</td></tr>
                    ) : (
                      books.map((b) => (
                        <tr key={b.id}>
                          <td>
                            {b.coverImageUrl ? (
                              <img src={getCoverUrl(b.coverImageUrl)} alt={b.title} className="table-thumb-img" />
                            ) : (
                              <div className="table-thumb">{getMonogram(b.title)}</div>
                            )}
                          </td>
                          <td><strong>{b.title}</strong></td>
                          <td>{b.author}</td>
                          <td><code>{b.isbn}</code></td>
                          <td className="text-right">
                            {userRole === 'ADMIN' ? (
                              <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
                                <button className="btn btn-secondary btn-sm" title="Upload Cover" onClick={() => { setSelectedBookForCover({ id: b.id, title: b.title }); setActiveModal('uploadCover'); }}>Cover</button>
                                <button className="btn btn-secondary btn-sm" onClick={() => { setBookForm(b); setActiveModal('editBook'); }}>Edit</button>
                                <button className="btn btn-danger btn-sm" onClick={() => handleDeleteBook(b.id, b.title)}>Del</button>
                              </div>
                            ) : (
                              <button className="btn btn-primary btn-sm" onClick={() => { setUserBorrowBookId(b.id); setActiveModal('userBorrow'); }}>Borrow</button>
                            )}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>

              <div className="pagination-footer mt-4">
                <span className="pagination-info">Showing page {booksPage + 1} of {booksTotalPages}</span>
                <div className="pagination-buttons">
                  <button className="btn btn-secondary btn-sm" disabled={booksPage <= 0} onClick={() => setBooksPage(prev => prev - 1)}>&larr; Previous</button>
                  <button className="btn btn-secondary btn-sm" disabled={booksPage >= booksTotalPages - 1} onClick={() => setBooksPage(prev => prev + 1)}>Next &rarr;</button>
                </div>
              </div>
            </div>
          )}

          {/* PAGE 3: MEMBERS */}
          {currentPage === 'members' && userRole === 'ADMIN' && (
            <div className="page-pane active">
              <div className="action-bar">
                <div className="filter-controls">
                  <div className="search-input-group">
                    <Search size={16} />
                    <input
                      type="text"
                      placeholder="Search member by name or email..."
                      value={memberSearchQuery}
                      onChange={(e) => setMemberSearchQuery(e.target.value)}
                    />
                  </div>
                  <button className="btn btn-secondary" onClick={() => { setMembersPage(0); loadMembers(0, memberSearchQuery); }}>Search</button>
                  <button className="btn btn-ghost" onClick={() => { setMemberSearchQuery(''); loadMembers(0, ''); }}>Clear</button>
                </div>

                <button className="btn btn-primary" onClick={() => { setUserForm({ id: '', name: '', email: '', password: '' }); setActiveModal('createUser'); }}>
                  <Plus size={16} />
                  <span>Register Member</span>
                </button>
              </div>

              <div className="table-container mt-4">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th width="60">ID</th>
                      <th>Member Name</th>
                      <th>Email Address</th>
                      <th width="180" className="text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {members.length === 0 ? (
                      <tr><td colSpan="4" className="empty-cell">No members found.</td></tr>
                    ) : (
                      members.map((m) => (
                        <tr key={m.id}>
                          <td>#{m.id}</td>
                          <td><strong>{m.name || m.username}</strong></td>
                          <td>{m.email}</td>
                          <td className="text-right">
                            <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
                              <button className="btn btn-secondary btn-sm" onClick={() => { setUserForm({ id: m.id, name: m.name || m.username, email: m.email, password: '' }); setActiveModal('editUser'); }}>Edit</button>
                              <button className="btn btn-danger btn-sm" onClick={() => handleDeleteUser(m.id, m.name || m.email)}>Delete</button>
                            </div>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* PAGE 4: BORROWED BOOKS */}
          {currentPage === 'borrow' && (
            <div className="page-pane active">
              {userRole === 'ADMIN' ? (
                <div className="section-card">
                  <div className="section-header flex-between">
                    <div>
                      <h3>All Borrowed Books (Circulation Log)</h3>
                      <p className="section-desc">Active and returned book loans across all registered library patrons</p>
                    </div>
                    <button className="btn btn-primary btn-sm" onClick={() => setActiveModal('adminBorrow')}>
                      + Issue Book Borrow
                    </button>
                  </div>

                  <div className="table-container mt-6">
                    <table className="data-table">
                      <thead>
                        <tr>
                          <th width="70">Cover</th>
                          <th>Book Title & Author</th>
                          <th>Borrower (Member)</th>
                          <th>Borrow Date</th>
                          <th>Due Date</th>
                          <th>Status</th>
                          <th className="text-right">Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {adminBorrows.length === 0 ? (
                          <tr><td colSpan="7" className="empty-cell">No active borrows log found.</td></tr>
                        ) : (
                          adminBorrows.map((b) => (
                            <tr key={b.id}>
                              <td>
                                {b.book?.coverImageUrl ? (
                                  <img src={getCoverUrl(b.book.coverImageUrl)} alt="Cover" className="table-thumb-img" />
                                ) : (
                                  <div className="table-thumb">{getMonogram(b.book?.title || 'B')}</div>
                                )}
                              </td>
                              <td><strong>{b.book?.title}</strong><br /><span className="text-muted">{b.book?.author}</span></td>
                              <td>{b.user?.name || b.user?.email || `User #${b.userId}`}</td>
                              <td>{b.borrowDate}</td>
                              <td>{b.dueDate}</td>
                              <td>
                                <span className={`badge ${b.returnDate ? 'badge-success' : 'badge-warning'}`}>
                                  {b.returnDate ? 'RETURNED' : 'ACTIVE'}
                                </span>
                              </td>
                              <td className="text-right">
                                {!b.returnDate && (
                                  <button className="btn btn-secondary btn-sm" onClick={() => handleReturnBook(b.id)}>Return</button>
                                )}
                              </td>
                            </tr>
                          ))
                        )}
                      </tbody>
                    </table>
                  </div>
                </div>
              ) : (
                <div className="section-card">
                  <div className="section-header flex-between">
                    <div>
                      <h3>My Borrowed Books</h3>
                      <p className="section-desc">Track your active reading loans, due dates, and return books</p>
                    </div>
                    <div style={{ display: 'flex', gap: '8px' }}>
                      <button className="btn btn-secondary btn-sm" onClick={() => setCurrentPage('books')}>Browse Catalog</button>
                      <button className="btn btn-primary btn-sm" onClick={() => setActiveModal('userBorrow')}>+ Borrow a Book</button>
                    </div>
                  </div>

                  <div className="table-container mt-6">
                    <table className="data-table">
                      <thead>
                        <tr>
                          <th width="70">Cover</th>
                          <th>Book Title</th>
                          <th>Author</th>
                          <th>Borrow Date</th>
                          <th>Due Date</th>
                          <th>Status</th>
                          <th className="text-right">Return Book</th>
                        </tr>
                      </thead>
                      <tbody>
                        {userBorrows.length === 0 ? (
                          <tr><td colSpan="7" className="empty-cell">You have no borrowed books currently.</td></tr>
                        ) : (
                          userBorrows.map((b) => (
                            <tr key={b.id}>
                              <td>
                                {b.book?.coverImageUrl ? (
                                  <img src={getCoverUrl(b.book.coverImageUrl)} alt="Cover" className="table-thumb-img" />
                                ) : (
                                  <div className="table-thumb">{getMonogram(b.book?.title || 'B')}</div>
                                )}
                              </td>
                              <td><strong>{b.book?.title}</strong></td>
                              <td>{b.book?.author}</td>
                              <td>{b.borrowDate}</td>
                              <td>{b.dueDate}</td>
                              <td>
                                <span className={`badge ${b.returnDate ? 'badge-success' : 'badge-warning'}`}>
                                  {b.returnDate ? 'RETURNED' : 'ACTIVE'}
                                </span>
                              </td>
                              <td className="text-right">
                                {!b.returnDate && (
                                  <button className="btn btn-secondary btn-sm" onClick={() => handleReturnBook(b.id)}>Check In</button>
                                )}
                              </td>
                            </tr>
                          ))
                        )}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* PAGE 5: FINES MANAGEMENT */}
          {currentPage === 'fines' && (
            <div className="page-pane active">
              <div className="section-card">
                <div className="section-header flex-between">
                  <div>
                    <h3>{userRole === 'ADMIN' ? 'Member Fine Settlement' : 'My Library Fines & Dues'}</h3>
                    <p className="section-desc">{userRole === 'ADMIN' ? 'Lookup member fines, outstanding balance, and settle payments' : 'View your outstanding dues and settle payments online'}</p>
                  </div>
                  <button className="btn btn-secondary btn-sm" onClick={loadFines}>
                    <RefreshCw size={14} style={{ marginRight: '4px' }} />
                    Refresh Fines
                  </button>
                </div>

                {userRole === 'ADMIN' && (
                  <div className="fine-lookup-bar mt-4">
                    <div className="form-group mb-0 flex-1">
                      <label className="form-label">Filter by Member</label>
                      <select
                        className="form-select"
                        value={selectedFineMemberId}
                        onChange={(e) => { setSelectedFineMemberId(e.target.value); loadFines(); }}
                      >
                        <option value="">-- All Library Members (All Fines) --</option>
                        {allMembersForFines.map(m => (
                          <option key={m.id} value={m.id}>{m.name || m.email} (#{m.id})</option>
                        ))}
                      </select>
                    </div>
                  </div>
                )}

                <div className="fine-summary-banner mt-6">
                  <div className="summary-item">
                    <span className="summary-label">Account / Scope:</span>
                    <span className="summary-value">{userRole === 'ADMIN' ? (selectedFineMemberId ? `Member #${selectedFineMemberId}` : 'All Members') : userName}</span>
                  </div>
                  <div className="summary-item text-right">
                    <span className="summary-label">Total Outstanding Balance:</span>
                    <span className="summary-amount text-danger">${fineTotalBalance.toFixed(2)}</span>
                  </div>
                </div>

                <div className="table-container mt-6">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Fine ID</th>
                        {userRole === 'ADMIN' && <th>Member</th>}
                        <th>Book Title & Author</th>
                        <th>Amount</th>
                        <th>Status</th>
                        <th className="text-right">Action</th>
                      </tr>
                    </thead>
                    <tbody>
                      {fines.length === 0 ? (
                        <tr><td colSpan={userRole === 'ADMIN' ? 6 : 5} className="empty-cell">No fine records found.</td></tr>
                      ) : (
                        fines.map(f => (
                          <tr key={f.id}>
                            <td>#{f.id}</td>
                            {userRole === 'ADMIN' && <td>{f.user?.name || f.user?.email || `User #${f.userId}`}</td>}
                            <td><strong>{f.borrow?.book?.title || 'Library Title'}</strong></td>
                            <td><strong>${(f.amount || 0).toFixed(2)}</strong></td>
                            <td>
                              <span className={`badge ${f.status === 'PAID' ? 'badge-success' : 'badge-danger'}`}>
                                {f.status}
                              </span>
                            </td>
                            <td className="text-right">
                              {f.status === 'UNPAID' && (
                                <button className="btn btn-primary btn-sm" onClick={() => handlePayFine(f.id)}>Settle & Pay</button>
                              )}
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {/* PAGE 6: MEMBERSHIP */}
          {currentPage === 'membership' && (
            <div className="page-pane active">
              {!membership || membership.status === 'NONE' || membership.status === 'CANCELLED' ? (
                <div className="section-card max-w-2xl" style={{ margin: '0 auto' }}>
                  <div className="section-header text-center">
                    <div className="brand-badge mb-4" style={{ margin: '0 auto', display: 'inline-flex', width: '64px', height: '64px', borderRadius: '50%', background: 'var(--primary-light)', alignItems: 'center', justifyContent: 'center', color: 'var(--primary)' }}>
                      <Award size={32} />
                    </div>
                    <h3>Apply for Library Membership</h3>
                    <p className="section-desc">Unlock premium member privileges including book borrowing and renewal</p>
                  </div>

                  <div className="membership-benefits mt-6 p-4 rounded-lg" style={{ background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.05)' }}>
                    <h4 className="text-sm font-semibold mb-3">Membership Benefits:</h4>
                    <ul className="benefit-list" style={{ listStyle: 'none', padding: 0, margin: 0 }}>
                      <li className="mb-2" style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                        <span style={{ color: 'var(--success)', fontWeight: 'bold' }}>✓</span> Borrow up to 5 books concurrently
                      </li>
                      <li className="mb-2" style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                        <span style={{ color: 'var(--success)', fontWeight: 'bold' }}>✓</span> Renew borrow terms online
                      </li>
                    </ul>
                  </div>

                  <button className="btn btn-primary btn-block mt-6" onClick={handleApplyMembership}>
                    Apply for Membership
                  </button>
                </div>
              ) : membership.status === 'PENDING' ? (
                <div className="section-card">
                  <div className="section-header">
                    <h3>Review & Sign Agreement</h3>
                    <p className="section-desc">Please review the agreement and upload your signature PNG to activate your membership</p>
                  </div>

                  <div className="grid-2col mt-6">
                    <div className="panel-agreement p-4 rounded-lg" style={{ background: 'var(--bg-surface-elevated)', border: '1px solid var(--border-color)', maxHeight: '400px', overflowY: 'auto' }}>
                      <div style={{ whiteSpace: 'pre-wrap', fontFamily: 'monospace', fontSize: '12px' }}>
                        {agreementText}
                      </div>
                    </div>

                    <div className="panel-signature" style={{ display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
                      <div className="form-group">
                        <label className="form-label">Upload Signature PNG</label>
                        <p className="section-desc mb-2">File must be a transparent PNG, maximum 50KB</p>

                        <div className="file-upload-box text-center p-6 rounded-lg" style={{ border: '2px dashed var(--border-color)', cursor: 'pointer' }} onClick={() => document.getElementById('sigFileElem').click()}>
                          <Upload size={36} className="mx-auto text-muted mb-2" style={{ margin: '0 auto 10px auto', display: 'block' }} />
                          <span className="text-sm font-medium block">{sigFile ? sigFile.name : 'Click to select PNG signature file'}</span>
                          <input
                            type="file"
                            id="sigFileElem"
                            className="hidden"
                            accept="image/png"
                            onChange={(e) => {
                              if (e.target.files && e.target.files[0]) {
                                setSigFile(e.target.files[0]);
                                setSigPreviewUrl(URL.createObjectURL(e.target.files[0]));
                              }
                            }}
                          />
                        </div>
                      </div>

                      {sigPreviewUrl && (
                        <div className="signature-preview-container mt-4" style={{ textAlign: 'center' }}>
                          <span className="form-label block text-left">Signature Preview:</span>
                          <div style={{ background: '#ffffff', padding: '10px', borderRadius: '4px', display: 'inline-block', marginTop: '5px', border: '1px solid var(--border-color)' }}>
                            <img src={sigPreviewUrl} alt="Preview" style={{ maxHeight: '80px', maxWidth: '240px', display: 'block' }} />
                          </div>
                        </div>
                      )}

                      <button className="btn btn-primary btn-block mt-6" disabled={!sigFile} onClick={handleSignatureSubmit}>
                        Sign & Activate Membership
                      </button>
                    </div>
                  </div>
                </div>
              ) : (
                <div>
                  <div className="grid-2col">
                    <div className="membership-card-badge" style={{ background: 'linear-gradient(135deg, #1e293b 0%, #0f172a 100%)', border: '1px solid #334155', borderRadius: '16px', padding: '24px', color: '#ffffff', minHeight: '220px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between', boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.3)', position: 'relative', overflow: 'hidden' }}>
                      <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                        <div>
                          <h4 style={{ fontSize: '18px', fontWeight: 'bold', letterSpacing: '0.05em', color: '#38bdf8', margin: 0 }}>ATHENAEUM</h4>
                          <span style={{ fontSize: '10px', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.1em' }}>Library System</span>
                        </div>
                        <span className="badge badge-success" style={{ background: 'rgba(34,197,94,0.15)', color: '#4ade80', border: '1px solid rgba(34,197,94,0.2)', padding: '4px 10px', borderRadius: '9999px', fontSize: '11px', fontWeight: '600' }}>ACTIVE</span>
                      </div>

                      <div className="card-body" style={{ marginTop: '20px' }}>
                        <span style={{ fontSize: '10px', color: '#64748b', display: 'block', textTransform: 'uppercase' }}>Membership ID</span>
                        <span style={{ fontSize: '22px', fontFamily: 'monospace', fontWeight: 'bold', letterSpacing: '2px', color: '#f8fafc' }}>{membership.membershipId || membership.id || 'MEM-0001'}</span>
                      </div>

                      <div className="card-footer" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginTop: '20px' }}>
                        <div>
                          <span style={{ fontSize: '9px', color: '#64748b', display: 'block', textTransform: 'uppercase' }}>Holder Name</span>
                          <span style={{ fontSize: '14px', fontWeight: 500, color: '#e2e8f0' }}>{userName}</span>
                        </div>
                        <div style={{ textAlign: 'right' }}>
                          <span style={{ fontSize: '9px', color: '#64748b', display: 'block', textTransform: 'uppercase' }}>Expires On</span>
                          <span style={{ fontSize: '14px', fontWeight: 500, color: '#e2e8f0' }}>{membership.expiryDate || membership.expirationDate || '2027-12-31'}</span>
                        </div>
                      </div>
                    </div>

                    <div className="section-card">
                      <div className="section-header">
                        <h3>Membership Account</h3>
                        <p className="section-desc">Verify status and download your signed agreement documents</p>
                      </div>

                      <div className="account-details mt-4">
                        <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 0', borderBottom: '1px solid var(--border-color)' }}>
                          <span className="text-muted">Membership Status</span>
                          <span style={{ color: 'var(--success)', fontWeight: 600 }}>Active</span>
                        </div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 0', borderBottom: '1px solid var(--border-color)' }}>
                          <span className="text-muted">Activated Date</span>
                          <span style={{ fontWeight: 500 }}>{membership.activatedAt ? new Date(membership.activatedAt).toLocaleDateString() : 'N/A'}</span>
                        </div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 0', borderBottom: '1px solid var(--border-color)' }}>
                          <span className="text-muted">Document Status</span>
                          <span style={{ color: 'var(--success)', fontWeight: 600 }}>PDF Signed</span>
                        </div>
                      </div>

                      <button className="btn btn-secondary btn-block mt-6" onClick={handleDownloadPdf}>
                        Download Signed PDF (Agreement)
                      </button>

                      <button className="btn btn-danger btn-block mt-3" onClick={handleCancelMembership}>
                        Cancel Membership
                      </button>
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}
        </main>
      </div>

      {/* MODALS */}
      {/* 1. Create Book Modal */}
      {activeModal === 'createBook' && (
        <div className="modal-backdrop open">
          <div className="modal-card">
            <div className="modal-header">
              <h3>Add Book to Inventory</h3>
              <button className="btn-close" onClick={() => setActiveModal(null)}>&times;</button>
            </div>
            <form onSubmit={handleCreateBookSubmit}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label">Book Title</label>
                  <input type="text" className="form-input" required value={bookForm.title} onChange={e => setBookForm({ ...bookForm, title: e.target.value })} />
                </div>
                <div className="form-group">
                  <label className="form-label">Author Name</label>
                  <input type="text" className="form-input" required value={bookForm.author} onChange={e => setBookForm({ ...bookForm, author: e.target.value })} />
                </div>
                <div className="form-group">
                  <label className="form-label">ISBN Number</label>
                  <input type="text" className="form-input" required value={bookForm.isbn} onChange={e => setBookForm({ ...bookForm, isbn: e.target.value })} />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-ghost" onClick={() => setActiveModal(null)}>Cancel</button>
                <button type="submit" className="btn btn-primary">Save Book</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 2. Edit Book Modal */}
      {activeModal === 'editBook' && (
        <div className="modal-backdrop open">
          <div className="modal-card">
            <div className="modal-header">
              <h3>Edit Book Details</h3>
              <button className="btn-close" onClick={() => setActiveModal(null)}>&times;</button>
            </div>
            <form onSubmit={handleEditBookSubmit}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label">Book Title</label>
                  <input type="text" className="form-input" required value={bookForm.title} onChange={e => setBookForm({ ...bookForm, title: e.target.value })} />
                </div>
                <div className="form-group">
                  <label className="form-label">Author Name</label>
                  <input type="text" className="form-input" required value={bookForm.author} onChange={e => setBookForm({ ...bookForm, author: e.target.value })} />
                </div>
                <div className="form-group">
                  <label className="form-label">ISBN Number</label>
                  <input type="text" className="form-input" required value={bookForm.isbn} onChange={e => setBookForm({ ...bookForm, isbn: e.target.value })} />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-ghost" onClick={() => setActiveModal(null)}>Cancel</button>
                <button type="submit" className="btn btn-primary">Save Changes</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 3. Upload Cover Modal */}
      {activeModal === 'uploadCover' && (
        <div className="modal-backdrop open">
          <div className="modal-card">
            <div className="modal-header">
              <h3>Upload Book Cover Image</h3>
              <button className="btn-close" onClick={() => setActiveModal(null)}>&times;</button>
            </div>
            <form onSubmit={handleUploadCoverSubmit}>
              <div className="modal-body">
                <p className="text-subtle mb-4">Select an image file for: <strong>{selectedBookForCover.title}</strong></p>
                <div className="form-group">
                  <label className="form-label">Cover Image File</label>
                  <input
                    type="file"
                    className="form-input"
                    accept="image/*"
                    required
                    onChange={e => {
                      if (e.target.files && e.target.files[0]) {
                        setCoverFile(e.target.files[0]);
                        setCoverPreview(URL.createObjectURL(e.target.files[0]));
                      }
                    }}
                  />
                </div>
                {coverPreview && (
                  <div className="cover-preview-box mt-4">
                    <img src={coverPreview} alt="Preview" />
                  </div>
                )}
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-ghost" onClick={() => setActiveModal(null)}>Cancel</button>
                <button type="submit" className="btn btn-primary">Upload & Apply</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 4. Create User Modal */}
      {activeModal === 'createUser' && (
        <div className="modal-backdrop open">
          <div className="modal-card">
            <div className="modal-header">
              <h3>Register New Member</h3>
              <button className="btn-close" onClick={() => setActiveModal(null)}>&times;</button>
            </div>
            <form onSubmit={handleCreateUserSubmit}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label">Member Name</label>
                  <input type="text" className="form-input" required value={userForm.name} onChange={e => setUserForm({ ...userForm, name: e.target.value })} />
                </div>
                <div className="form-group">
                  <label className="form-label">Email Address</label>
                  <input type="email" className="form-input" required value={userForm.email} onChange={e => setUserForm({ ...userForm, email: e.target.value })} />
                </div>
                <div className="form-group">
                  <label className="form-label">Password</label>
                  <input type="password" className="form-input" required minLength="8" value={userForm.password} onChange={e => setUserForm({ ...userForm, password: e.target.value })} />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-ghost" onClick={() => setActiveModal(null)}>Cancel</button>
                <button type="submit" className="btn btn-primary">Register Member</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 5. Edit User Modal */}
      {activeModal === 'editUser' && (
        <div className="modal-backdrop open">
          <div className="modal-card">
            <div className="modal-header">
              <h3>Edit Member Profile</h3>
              <button className="btn-close" onClick={() => setActiveModal(null)}>&times;</button>
            </div>
            <form onSubmit={handleEditUserSubmit}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label">Member Name</label>
                  <input type="text" className="form-input" required value={userForm.name} onChange={e => setUserForm({ ...userForm, name: e.target.value })} />
                </div>
                <div className="form-group">
                  <label className="form-label">Email Address</label>
                  <input type="email" className="form-input" required value={userForm.email} onChange={e => setUserForm({ ...userForm, email: e.target.value })} />
                </div>
                <div className="form-group">
                  <label className="form-label">Change Password (optional)</label>
                  <input type="password" className="form-input" placeholder="Leave blank to keep unchanged" value={userForm.password} onChange={e => setUserForm({ ...userForm, password: e.target.value })} />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-ghost" onClick={() => setActiveModal(null)}>Cancel</button>
                <button type="submit" className="btn btn-primary">Save Changes</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 6. Admin Issue Borrow Modal */}
      {activeModal === 'adminBorrow' && (
        <div className="modal-backdrop open">
          <div className="modal-card">
            <div className="modal-header">
              <h3>Issue Book Borrow (Circulation)</h3>
              <button className="btn-close" onClick={() => setActiveModal(null)}>&times;</button>
            </div>
            <form onSubmit={handleAdminIssueLoan}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label">Select Library Member</label>
                  <select className="form-select" required value={adminBorrowSelect.memberId} onChange={e => setAdminBorrowSelect({ ...adminBorrowSelect, memberId: e.target.value })}>
                    <option value="">-- Choose Member --</option>
                    {allMembersForFines.map(m => (
                      <option key={m.id} value={m.id}>{m.name || m.email}</option>
                    ))}
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Select Book Title</label>
                  <select className="form-select" required value={adminBorrowSelect.bookId} onChange={e => setAdminBorrowSelect({ ...adminBorrowSelect, bookId: e.target.value })}>
                    <option value="">-- Choose Book --</option>
                    {books.map(b => (
                      <option key={b.id} value={b.id}>{b.title}</option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-ghost" onClick={() => setActiveModal(null)}>Cancel</button>
                <button type="submit" className="btn btn-primary">Confirm & Issue Borrow</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 7. User Self Borrow Modal */}
      {activeModal === 'userBorrow' && (
        <div className="modal-backdrop open">
          <div className="modal-card">
            <div className="modal-header">
              <h3>Borrow a Catalog Title</h3>
              <button className="btn-close" onClick={() => setActiveModal(null)}>&times;</button>
            </div>
            <form onSubmit={handleUserSelfBorrow}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label">Select Book from Catalog</label>
                  <select className="form-select" required value={userBorrowBookId} onChange={e => setUserBorrowBookId(e.target.value)}>
                    <option value="">-- Choose Book --</option>
                    {books.map(b => (
                      <option key={b.id} value={b.id}>{b.title} (by {b.author})</option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-ghost" onClick={() => setActiveModal(null)}>Cancel</button>
                <button type="submit" className="btn btn-primary">Check Out Book</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 8. Confirmation Dialog Modal */}
      {activeModal === 'confirm' && (
        <div className="modal-backdrop open">
          <div className="modal-card max-w-sm">
            <div className="modal-header">
              <h3>{confirmConfig.title}</h3>
              <button className="btn-close" onClick={() => setActiveModal(null)}>&times;</button>
            </div>
            <div className="modal-body">
              <p className="text-subtle">{confirmConfig.message}</p>
            </div>
            <div className="modal-footer">
              <button className="btn btn-ghost" onClick={() => setActiveModal(null)}>Cancel</button>
              <button
                className="btn btn-danger"
                onClick={() => {
                  if (confirmConfig.onConfirm) confirmConfig.onConfirm();
                  setActiveModal(null);
                }}
              >
                {confirmConfig.actionBtnText}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 9. Google OAuth Manual Token Entry Modal */}
      {activeModal === 'googleToken' && (
        <div className="modal-backdrop open">
          <div className="modal-card">
            <div className="modal-header">
              <h3>Complete Google Sign In</h3>
              <button className="btn-close" onClick={() => setActiveModal(null)}>&times;</button>
            </div>
            <form onSubmit={(e) => {
              e.preventDefault();
              try {
                const parsed = JSON.parse(manualOAuthToken);
                if (parsed.token) {
                  setAuthToken(parsed.token);
                  showToast('Signed in with Google successfully', 'success');
                  setActiveModal(null);
                } else {
                  showToast('Invalid token format', 'error');
                }
              } catch (err) {
                showToast('Invalid JSON token format', 'error');
              }
            }}>
              <div className="modal-body">
                <p className="text-subtle mb-4">Paste your token response payload below to enter the dashboard:</p>
                <div className="form-group">
                  <label className="form-label">Authentication Token Payload</label>
                  <textarea
                    className="form-input"
                    rows={4}
                    placeholder='{"token": "eyJhbGci..."}'
                    required
                    value={manualOAuthToken}
                    onChange={e => setManualOAuthToken(e.target.value)}
                  />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-ghost" onClick={() => setActiveModal(null)}>Cancel</button>
                <button type="submit" className="btn btn-primary">Sign In to Dashboard</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* TOAST NOTIFICATIONS */}
      <div className="toast-container">
        {toasts.map(t => (
          <div key={t.id} className={`toast ${t.type}`}>
            {t.message}
          </div>
        ))}
      </div>
    </div>
  );
}
