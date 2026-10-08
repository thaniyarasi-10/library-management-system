import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useAuth0 } from '@auth0/auth0-react';
import { jwtDecode } from 'jwt-decode';
import {
  BookOpen, Users, LayoutDashboard, CreditCard, Award, Sun, Moon,
  Plus, Search, LogOut, X, ChevronDown, Check, Shield, FileText, Upload, RefreshCw,
  User as UserIcon, Edit2, Phone, Gift
} from 'lucide-react';
import './styles.css';

// Supported country codes for mobile number capture with country-specific validation rules
const COUNTRY_CODES = [
  { code: 'US', dial: '+1', name: 'United States', min: 10, max: 10, regex: /^[2-9]\d{9}$/, hint: '10 digits (e.g. 555 555 5555)' },
  { code: 'CA', dial: '+1', name: 'Canada', min: 10, max: 10, regex: /^[2-9]\d{9}$/, hint: '10 digits (e.g. 555 555 5555)' },
  { code: 'IN', dial: '+91', name: 'India', min: 10, max: 10, regex: /^[6-9]\d{9}$/, hint: '10 digits starting with 6-9' },
  { code: 'GB', dial: '+44', name: 'United Kingdom', min: 10, max: 10, regex: /^7\d{9}$/, hint: '10 digits starting with 7' },
  { code: 'AU', dial: '+61', name: 'Australia', min: 9, max: 9, regex: /^4\d{8}$/, hint: '9 digits starting with 4' },
  { code: 'DE', dial: '+49', name: 'Germany', min: 10, max: 11, regex: /^1[5-7]\d{8,9}$/, hint: '10-11 digits starting with 15/16/17' },
  { code: 'FR', dial: '+33', name: 'France', min: 9, max: 9, regex: /^[67]\d{8}$/, hint: '9 digits starting with 6 or 7' },
  { code: 'JP', dial: '+81', name: 'Japan', min: 10, max: 10, regex: /^[789]0\d{8}$/, hint: '10 digits starting with 70, 80 or 90' },
  { code: 'SG', dial: '+65', name: 'Singapore', min: 8, max: 8, regex: /^[89]\d{7}$/, hint: '8 digits starting with 8 or 9' },
  { code: 'AE', dial: '+971', name: 'UAE', min: 9, max: 9, regex: /^5\d{8}$/, hint: '9 digits starting with 5' }
];

const validatePhoneNumber = (countryCodeDial, localDigits) => {
  const cleanDigits = (localDigits || '').replace(/\D/g, '');
  if (!cleanDigits) {
    return { isValid: false, error: 'Mobile number is required', cleanDigits: '' };
  }

  const country = COUNTRY_CODES.find(c => c.dial === countryCodeDial);
  if (!country) {
    if (cleanDigits.length < 7 || cleanDigits.length > 15) {
      return { isValid: false, error: 'Please enter a valid phone number (7-15 digits)', cleanDigits };
    }
    return { isValid: true, error: '', cleanDigits };
  }

  if (country.min === country.max && cleanDigits.length !== country.min) {
    return {
      isValid: false,
      error: `Please enter a valid ${country.name} mobile number (${country.min} digits). You entered ${cleanDigits.length} digit${cleanDigits.length === 1 ? '' : 's'}.`,
      cleanDigits
    };
  }

  if (cleanDigits.length < country.min || cleanDigits.length > country.max) {
    return {
      isValid: false,
      error: `Please enter a valid ${country.name} mobile number (${country.min}-${country.max} digits). You entered ${cleanDigits.length} digit${cleanDigits.length === 1 ? '' : 's'}.`,
      cleanDigits
    };
  }

  if (country.regex && !country.regex.test(cleanDigits)) {
    return {
      isValid: false,
      error: `Invalid ${country.name} mobile number format. Expected: ${country.hint}.`,
      cleanDigits
    };
  }

  return { isValid: true, error: '', cleanDigits };
};

const parsePhoneNumber = (fullPhone) => {
  if (!fullPhone || !fullPhone.startsWith('+')) {
    return { countryDial: '+1', localNumber: fullPhone ? fullPhone.replace(/\D/g, '') : '' };
  }
  // Try longest matching prefix from COUNTRY_CODES
  const sorted = [...COUNTRY_CODES].sort((a, b) => b.dial.length - a.dial.length);
  for (const c of sorted) {
    if (fullPhone.startsWith(c.dial)) {
      return { countryDial: c.dial, localNumber: fullPhone.slice(c.dial.length) };
    }
  }
  return { countryDial: '+1', localNumber: fullPhone.slice(1) };
};

export default function App() {
  // Theme state
  const [theme, setTheme] = useState(localStorage.getItem('athenaeum_theme') || 'dark');

  // Auth0 state
  const {
    isAuthenticated,
    isLoading,
    user: auth0User,
    loginWithRedirect,
    logout,
    getAccessTokenSilently,
    error: auth0Error
  } = useAuth0();

  const [currentUser, setCurrentUser] = useState(null);
  const [userRole, setUserRole] = useState('');
  const [showUserDropdown, setShowUserDropdown] = useState(false);
  const [authMode, setAuthMode] = useState('signin');
  const [signupUsername, setSignupUsername] = useState('');
  const [signupError, setSignupError] = useState('');
  const userFetchedRef = useRef(false);

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
  const [memberSortBy, setMemberSortBy] = useState('id');
  const [memberSortDir, setMemberSortDir] = useState('asc');

  // Borrows
  const [adminBorrows, setAdminBorrows] = useState([]);
  const [userBorrows, setUserBorrows] = useState([]);
  const [modalBooks, setModalBooks] = useState([]);

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

  // Book Donations
  const [donations, setDonations] = useState([]);
  const [donationFilter, setDonationFilter] = useState('ALL');
  const [donationForm, setDonationForm] = useState({ title: '', author: '', isbn: '', donatedBookCount: 1, coverFile: null, coverPreview: '' });
  const [editDonationForm, setEditDonationForm] = useState({ id: null, title: '', author: '', isbn: '', donatedBookCount: 1, coverFile: null, coverPreview: '', existingCoverUrl: '' });
  const [isSubmittingDonation, setIsSubmittingDonation] = useState(false);
  const [rejectionModal, setRejectionModal] = useState({ open: false, donationId: null, reason: '' });

  // Modals & Drawers
  const [activeModal, setActiveModal] = useState(null); // 'createBook', 'editBook', 'createDonation', 'editDonation', 'createUser', 'editUser', 'adminBorrow', 'userBorrow', 'confirm'
  const [drawerData, setDrawerData] = useState(null); // { type: 'book'|'member', data: obj }
  const [confirmConfig, setConfirmConfig] = useState({ title: '', message: '', actionBtnText: 'Confirm', onConfirm: null });

  // Form models
  const [bookForm, setBookForm] = useState({ id: '', title: '', author: '', isbn: '', totalBookCount: 1, coverFile: null, coverPreview: '', existingCoverUrl: '' });
  const [userForm, setUserForm] = useState({ id: '', name: '', email: '', password: '', countryCode: '+1', phoneNumber: '', phoneError: '' });
  const [profileForm, setProfileForm] = useState({ name: '', email: '', countryCode: '+1', phoneNumber: '', phoneError: '' });
  const [promptPhoneForm, setPromptPhoneForm] = useState({ countryCode: '+1', phoneNumber: '', phoneError: '', isSubmitting: false });
  const [selectedBookForCover, setSelectedBookForCover] = useState({ id: '', title: '' });
  const [coverFile, setCoverFile] = useState(null);
  const [coverPreview, setCoverPreview] = useState('');
  const [adminBorrowSelect, setAdminBorrowSelect] = useState({ memberId: '', bookId: '' });
  const [userBorrowBookId, setUserBorrowBookId] = useState('');

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

  const fetchApi = useCallback(async (endpoint, options = {}) => {
    const headers = {
      ...(options.headers || {})
    };

    if (isAuthenticated && !headers['Authorization']) {
      try {
        const token = await getAccessTokenSilently();
        headers['Authorization'] = `Bearer ${token}`;
      } catch (err) {
        console.warn('Could not retrieve access token silently:', err);
      }
    }

    if (!(options.body instanceof FormData) && !headers['Content-Type']) {
      headers['Content-Type'] = 'application/json';
    }

    try {
      const res = await fetch(`${baseUrl}${endpoint}`, { ...options, headers });
      if (res.status === 401) {
        showToast('Session expired or unauthenticated. Please sign in again.', 'error');
        return { ok: false, status: 401 };
      }
      if (res.status === 403) {
        showToast('Access denied: You lack permission for this action.', 'error');
        return { ok: false, status: 403 };
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
  }, [isAuthenticated, getAccessTokenSilently]);

  // Theme Sync
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('athenaeum_theme', theme);
  }, [theme]);

  // Reusable profile refresh to keep reward points and user metadata up-to-date
  const refreshUserProfile = useCallback(async () => {
    if (!isAuthenticated) return;
    try {
      let reqHeaders = {};
      const pendingUsername = sessionStorage.getItem('auth0_pending_username');
      if (pendingUsername && pendingUsername.trim()) {
        reqHeaders['X-User-Username'] = pendingUsername.trim();
        reqHeaders['X-User-Name'] = pendingUsername.trim();
      }
      const res = await fetchApi('/user/me', { headers: reqHeaders });
      if (res.ok && res.data) {
        setCurrentUser(res.data);
        if (pendingUsername) sessionStorage.removeItem('auth0_pending_username');
      }
    } catch (err) {
      console.warn('Error refreshing user profile:', err);
    }
  }, [isAuthenticated, fetchApi]);

  // Auth Initialization and User Sync
  useEffect(() => {
    if (!isAuthenticated) {
      userFetchedRef.current = false;
      return;
    }

    if (userFetchedRef.current) return;
    userFetchedRef.current = true;

    // Helper to test if a value indicates ADMIN
    const checkIsAdmin = (claim) => {
      if (!claim) return false;
      if (typeof claim === 'string') {
        const c = claim.toUpperCase().trim();
        return c === 'ADMIN' || c === 'ROLE_ADMIN' || c.includes('ADMIN');
      }
      if (Array.isArray(claim)) {
        return claim.some(r => typeof r === 'string' && (
          r.toUpperCase().trim() === 'ADMIN' ||
          r.toUpperCase().trim() === 'ROLE_ADMIN' ||
          r.toLowerCase().includes('admin') ||
          r === 'books:write'
        ));
      }
      return false;
    };

    // Determine role from all standard Auth0 claim formats
    const findRoleFromUser = (userObj) => {
      if (!userObj) return false;
      const candidates = [
        userObj['https://library.kovanlabs.com/roles'],
        userObj['https://library.kovanlabs.com/role'],
        userObj['roles'],
        userObj['role'],
        userObj['permissions'],
        userObj['user_metadata']?.role,
        userObj['user_metadata']?.roles,
        userObj['app_metadata']?.role,
        userObj['app_metadata']?.roles
      ];
      return candidates.some(checkIsAdmin);
    };

    let isAdmin = findRoleFromUser(auth0User);
    setUserRole(isAdmin ? 'ADMIN' : 'USER');

    // Also inspect Access Token JWT & sync user profile with backend MySQL via GET /user/me
    const syncUser = async () => {
      // Check Access Token JWT for roles if ID token didn't have it
      if (!isAdmin && getAccessTokenSilently) {
        try {
          const token = await getAccessTokenSilently();
          if (token && token.includes('.')) {
            const decoded = jwtDecode(token);
            if (findRoleFromUser(decoded)) {
              isAdmin = true;
              setUserRole('ADMIN');
            }
          }
        } catch (tokErr) {
          console.warn('Access token decoding error:', tokErr);
        }
      }

      let reqHeaders = {};
      const pendingUsername = sessionStorage.getItem('auth0_pending_username');
      if (pendingUsername && pendingUsername.trim()) {
        reqHeaders['X-User-Username'] = pendingUsername.trim();
        reqHeaders['X-User-Name'] = pendingUsername.trim();
      }

      const res = await fetchApi('/user/me', { headers: reqHeaders });
      if (res.ok && res.data) {
        setCurrentUser(res.data);
        sessionStorage.removeItem('auth0_pending_username');
        if (!res.data.phone && !sessionStorage.getItem('phone_prompt_dismissed')) {
          setPromptPhoneForm({ countryCode: '+1', phoneNumber: '', phoneError: '', isSubmitting: false });
          setActiveModal('missingPhonePrompt');
        }
      } else if (auth0User) {
        setCurrentUser({
          email: auth0User.email || '',
          name: auth0User.name || auth0User.nickname || pendingUsername || 'User'
        });
        sessionStorage.removeItem('auth0_pending_username');
      }
    };

    syncUser();
  }, [isAuthenticated, auth0User, fetchApi, getAccessTokenSilently]);

  // Load Page Data on Change - Only fetch what is required for current page
  useEffect(() => {
    if (!isAuthenticated) return;
    if (currentPage === 'dashboard') {
      loadDashboard();
    } else if (currentPage === 'books') {
      loadBooks(booksPage, bookSearchQuery);
    } else if (currentPage === 'members') {
      if (userRole === 'ADMIN') loadMembers(membersPage, memberSearchQuery, memberSortBy, memberSortDir);
    } else if (currentPage === 'borrow') {
      loadBorrows();
    } else if (currentPage === 'fines') {
      loadFines();
    } else if (currentPage === 'membership') {
      loadMembership(true);
    } else if (currentPage === 'donations') {
      loadDonations();
    }
  }, [currentPage, isAuthenticated, userRole, booksPage, membersPage, memberSearchQuery, memberSortBy, memberSortDir]);

  const handleSignOut = () => {
    userFetchedRef.current = false;
    setCurrentUser(null);
    setUserRole('');
    setCurrentPage('dashboard');
    logout({ logoutParams: { returnTo: window.location.origin } });
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

  // Dashboard Loader - Fetches only essential summary data for the dashboard view
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
      // Only check membership status without pulling heavy agreement text
      loadMembership(false);
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
      body: JSON.stringify({
        title: bookForm.title,
        author: bookForm.author,
        isbn: bookForm.isbn,
        totalBookCount: Number(bookForm.totalBookCount) || 1
      })
    });
    if (res.ok) {
      const createdBook = res.data;
      if (bookForm.coverFile && createdBook?.id) {
        const formData = new FormData();
        formData.append('file', bookForm.coverFile);
        await fetchApi(`/books/${createdBook.id}/cover`, {
          method: 'POST',
          body: formData
        });
      }
      showToast('Book added to inventory successfully', 'success');
      setActiveModal(null);
      setBookForm({ id: '', title: '', author: '', isbn: '', totalBookCount: 1, coverFile: null, coverPreview: '', existingCoverUrl: '' });
      loadBooks(booksPage, bookSearchQuery);
    } else {
      showToast(res.data?.message || 'Failed to create book', 'error');
    }
  };

  const handleEditBookSubmit = async (e) => {
    e.preventDefault();
    const res = await fetchApi(`/books/${bookForm.id}`, {
      method: 'PUT',
      body: JSON.stringify({
        title: bookForm.title,
        author: bookForm.author,
        isbn: bookForm.isbn,
        totalBookCount: Number(bookForm.totalBookCount) || 1
      })
    });
    if (res.ok) {
      if (bookForm.coverFile) {
        const formData = new FormData();
        formData.append('file', bookForm.coverFile);
        await fetchApi(`/books/${bookForm.id}/cover`, {
          method: 'POST',
          body: formData
        });
      }
      showToast('Book updated successfully', 'success');
      setActiveModal(null);
      setBookForm({ id: '', title: '', author: '', isbn: '', totalBookCount: 1, coverFile: null, coverPreview: '', existingCoverUrl: '' });
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

  // Donations Loader & Handlers
  const loadDonations = async () => {
    const endpoint = userRole === 'ADMIN' ? '/donations' : '/donations/me';
    const res = await fetchApi(endpoint);
    if (res.ok && res.data) {
      const list = Array.isArray(res.data) ? res.data : (res.data.content || []);
      setDonations(list);
    }
  };

  const handleCreateDonationSubmit = async (e) => {
    e.preventDefault();
    if (isSubmittingDonation) return;
    setIsSubmittingDonation(true);
    try {
      let res;
      if (donationForm.coverFile) {
        const formData = new FormData();
        formData.append('title', donationForm.title);
        formData.append('author', donationForm.author);
        formData.append('isbn', donationForm.isbn);
        formData.append('donatedBookCount', Number(donationForm.donatedBookCount) || 1);
        formData.append('file', donationForm.coverFile);
        res = await fetchApi('/donations', {
          method: 'POST',
          body: formData
        });
      } else {
        const payload = {
          title: donationForm.title,
          author: donationForm.author,
          isbn: donationForm.isbn,
          donatedBookCount: Number(donationForm.donatedBookCount) || 1
        };
        res = await fetchApi('/donations', {
          method: 'POST',
          body: JSON.stringify(payload)
        });
      }
      if (res.ok) {
        showToast('Thank you! Your donation request has been submitted for review.', 'success');
        setActiveModal(null);
        setDonationForm({ title: '', author: '', isbn: '', donatedBookCount: 1, coverFile: null, coverPreview: '' });
        loadDonations();
        refreshUserProfile();
      } else {
        showToast(res.data?.message || 'Failed to submit donation request', 'error');
      }
    } finally {
      setIsSubmittingDonation(false);
    }
  };

  const handleOpenEditDonation = (donation) => {
    setEditDonationForm({
      id: donation.id,
      title: donation.title || '',
      author: donation.author || '',
      isbn: donation.isbn || '',
      donatedBookCount: donation.donatedBookCount || 1,
      coverFile: null,
      coverPreview: '',
      existingCoverUrl: donation.coverImageUrl || ''
    });
    setActiveModal('editDonation');
  };

  const handleEditDonationSubmit = async (e) => {
    e.preventDefault();
    if (isSubmittingDonation) return;
    setIsSubmittingDonation(true);
    try {
      let res;
      if (editDonationForm.coverFile) {
        const formData = new FormData();
        formData.append('title', editDonationForm.title);
        formData.append('author', editDonationForm.author);
        formData.append('isbn', editDonationForm.isbn);
        formData.append('donatedBookCount', Number(editDonationForm.donatedBookCount) || 1);
        formData.append('file', editDonationForm.coverFile);
        res = await fetchApi(`/donations/${editDonationForm.id}`, {
          method: 'PUT',
          body: formData
        });
      } else {
        const payload = {
          title: editDonationForm.title,
          author: editDonationForm.author,
          isbn: editDonationForm.isbn,
          donatedBookCount: Number(editDonationForm.donatedBookCount) || 1
        };
        res = await fetchApi(`/donations/${editDonationForm.id}`, {
          method: 'PUT',
          body: JSON.stringify(payload)
        });
      }
      if (res.ok) {
        showToast('Donation request updated successfully!', 'success');
        setActiveModal(null);
        loadDonations();
      } else {
        showToast(res.data?.message || 'Failed to update donation request', 'error');
      }
    } finally {
      setIsSubmittingDonation(false);
    }
  };

  const handleDeleteDonation = (donationId, title) => {
    setConfirmConfig({
      title: 'Delete Book Donation',
      message: `Are you sure you want to delete the donation request for "${title}" (Donation #${donationId})?`,
      actionBtnText: 'Delete Donation',
      onConfirm: async () => {
        const res = await fetchApi(`/donations/${donationId}`, { method: 'DELETE' });
        if (res.ok) {
          showToast('Donation request deleted successfully', 'success');
          loadDonations();
          refreshUserProfile();
        } else {
          showToast(res.data?.message || 'Failed to delete donation request', 'error');
        }
      }
    });
    setActiveModal('confirm');
  };

  const handleApproveDonation = (donationId) => {
    if (!donationId) return;
    setConfirmConfig({
      title: 'Approve Book Donation',
      message: `Are you sure you want to approve Donation #${donationId}? This will automatically add copies to the catalog and award donor reward points.`,
      actionBtnText: 'Approve Donation',
      onConfirm: async () => {
        const res = await fetchApi(`/donations/${donationId}/approve`, { method: 'POST' });
        if (res.ok) {
          showToast('Donation approved, added to catalog, and donor reward points awarded!', 'success');
          loadDonations();
          refreshUserProfile();
        } else {
          showToast(res.data?.message || 'Failed to approve donation', 'error');
        }
      }
    });
    setActiveModal('confirm');
  };

  const handleRejectDonationSubmit = async (e) => {
    e.preventDefault();
    const res = await fetchApi(`/donations/${rejectionModal.donationId}/reject`, {
      method: 'POST',
      body: JSON.stringify({ reason: rejectionModal.reason })
    });
    if (res.ok) {
      showToast('Donation request rejected', 'info');
      setRejectionModal({ open: false, donationId: null, reason: '' });
      loadDonations();
    } else {
      showToast(res.data?.message || 'Failed to reject donation', 'error');
    }
  };

  // Members Loader
  const loadMembers = async (page = 0, query = '', sortBy = memberSortBy, sortDir = memberSortDir) => {
    let url = `/user?page=${page}&size=10&sortBy=${sortBy}&sortDir=${sortDir}`;
    if (query) url = `/user/search?query=${encodeURIComponent(query)}&page=${page}&size=10&sortBy=${sortBy}&sortDir=${sortDir}`;
    const res = await fetchApi(url);
    if (res.ok && res.data) {
      setMembers(res.data.content || res.data || []);
      setMembersTotalPages(res.data.totalPages || 1);
    }
  };

  const handleCreateUserSubmit = async (e) => {
    e.preventDefault();
    let formattedPhone = null;
    if (userForm.phoneNumber && userForm.phoneNumber.trim()) {
      const validation = validatePhoneNumber(userForm.countryCode, userForm.phoneNumber);
      if (!validation.isValid) {
        setUserForm(prev => ({ ...prev, phoneError: validation.error }));
        return;
      }
      formattedPhone = `${userForm.countryCode}${validation.cleanDigits}`;
    }

    const payload = {
      name: userForm.name,
      email: userForm.email,
      password: userForm.password,
      ...(formattedPhone ? { phone: formattedPhone } : {})
    };

    const res = await fetchApi('/user', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
    if (res.ok) {
      showToast('Member registered successfully', 'success');
      setActiveModal(null);
      setUserForm({ id: '', name: '', email: '', password: '', countryCode: '+1', phoneNumber: '', phoneError: '' });
      loadMembers(membersPage, memberSearchQuery);
    } else {
      const errMsg = res.data?.message || 'Failed to register member';
      if (errMsg.toLowerCase().includes('phone') || errMsg.toLowerCase().includes('mobile')) {
        setUserForm(prev => ({ ...prev, phoneError: errMsg }));
      }
      showToast(errMsg, 'error');
    }
  };

  const handleEditUserSubmit = async (e) => {
    e.preventDefault();
    let formattedPhone = null;
    if (userForm.phoneNumber && userForm.phoneNumber.trim()) {
      const validation = validatePhoneNumber(userForm.countryCode, userForm.phoneNumber);
      if (!validation.isValid) {
        setUserForm(prev => ({ ...prev, phoneError: validation.error }));
        return;
      }
      formattedPhone = `${userForm.countryCode}${validation.cleanDigits}`;
    }

    const payload = {
      name: userForm.name,
      email: userForm.email,
      phone: formattedPhone
    };
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
      const errMsg = res.data?.message || 'Failed to update member';
      if (errMsg.toLowerCase().includes('phone') || errMsg.toLowerCase().includes('mobile')) {
        setUserForm(prev => ({ ...prev, phoneError: errMsg }));
      }
      showToast(errMsg, 'error');
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

  // Borrows Loader - Only loads active circulation records for the current view
  const loadBorrows = async () => {
    if (userRole === 'ADMIN') {
      const res = await fetchApi('/borrow');
      if (res.ok && res.data) {
        setAdminBorrows(Array.isArray(res.data) ? res.data : []);
      }
    } else {
      const res = await fetchApi('/borrow/me');
      if (res.ok && res.data) {
        setUserBorrows(Array.isArray(res.data) ? res.data : []);
      }
    }
  };

  const handleOpenAdminBorrowModal = async () => {
    setActiveModal('adminBorrow');
    if (allMembersForFines.length === 0) {
      const resM = await fetchApi('/user?page=0&size=100');
      if (resM.ok && resM.data) setAllMembersForFines(resM.data.content || resM.data || []);
    }
    if (modalBooks.length === 0) {
      const resB = await fetchApi('/books?page=0&size=100');
      if (resB.ok && resB.data) setModalBooks(resB.data.content || resB.data || []);
    }
  };

  const handleOpenUserBorrowModal = async () => {
    setActiveModal('userBorrow');
    if (modalBooks.length === 0) {
      const resB = await fetchApi('/books?page=0&size=100');
      if (resB.ok && resB.data) setModalBooks(resB.data.content || resB.data || []);
    }
  };

  const handleAdminIssueLoan = async (e) => {
    e.preventDefault();
    const memberUuidOrId = adminBorrowSelect.memberId;
    const bookUuidOrId = adminBorrowSelect.bookId;

    if (!memberUuidOrId || !bookUuidOrId) {
      showToast('Please select both a member and a book', 'error');
      return;
    }

    const isUuid = (val) => typeof val === 'string' && val.includes('-');
    const payload = {
      ...(isUuid(memberUuidOrId) ? { userUuid: memberUuidOrId } : { userId: Number(memberUuidOrId) || undefined }),
      ...(isUuid(bookUuidOrId) ? { bookUuid: bookUuidOrId } : { bookId: Number(bookUuidOrId) || undefined })
    };

    const res = await fetchApi('/borrow', {
      method: 'POST',
      body: JSON.stringify(payload)
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
    if (e && e.preventDefault) e.preventDefault();
    if (!isAuthenticated) {
      showToast('Please sign in to borrow books', 'error');
      return;
    }

    if (!userBorrowBookId) {
      showToast('Please select a book to borrow', 'error');
      return;
    }

    // Check membership status
    const resMem = await fetchApi('/memberships/me');
    if (!resMem.ok || !resMem.data || resMem.data.status !== 'ACTIVE') {
      showToast('Active membership required to check out books', 'error');
      setActiveModal(null);
      setCurrentPage('membership');
      return;
    }

    const candidateBooks = modalBooks.length > 0 ? modalBooks : books;
    const matchedBook = candidateBooks.find(b => b.uuid === userBorrowBookId || b.id === userBorrowBookId || String(b.uuid) === String(userBorrowBookId) || String(b.id) === String(userBorrowBookId));
    const targetUuid = matchedBook?.uuid || userBorrowBookId;

    const payload = {
      bookUuid: targetUuid,
      ...(matchedBook?.id ? { bookId: Number(matchedBook.id) } : {}),
      ...(currentUser?.uuid ? { userUuid: currentUser.uuid } : {}),
      ...(currentUser?.id ? { userId: Number(currentUser.id) } : {})
    };

    const res = await fetchApi('/borrow', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
    if (res.ok) {
      showToast('Book checked out successfully!', 'success');
      setActiveModal(null);
      if (currentPage === 'borrow') {
        loadBorrows();
      } else {
        setCurrentPage('borrow');
      }
    } else {
      showToast(res.data?.message || 'Failed to borrow book', 'error');
    }
  };

  const handleDirectBorrow = async (bookUuidOrId, bookTitle) => {
    if (!isAuthenticated) {
      showToast('Please sign in to borrow books', 'error');
      return;
    }

    if (!bookUuidOrId) {
      showToast('Invalid book selected', 'error');
      return;
    }

    // Check membership
    const resMem = await fetchApi('/memberships/me');
    if (!resMem.ok || !resMem.data || resMem.data.status !== 'ACTIVE') {
      showToast('Active membership required to check out books', 'error');
      setCurrentPage('membership');
      return;
    }

    const candidateBooks = modalBooks.length > 0 ? modalBooks : books;
    const matchedBook = candidateBooks.find(b => b.uuid === bookUuidOrId || b.id === bookUuidOrId || String(b.uuid) === String(bookUuidOrId) || String(b.id) === String(bookUuidOrId));
    const targetUuid = matchedBook?.uuid || (typeof bookUuidOrId === 'string' ? bookUuidOrId : String(bookUuidOrId));

    const payload = {
      bookUuid: targetUuid,
      ...(matchedBook?.id ? { bookId: Number(matchedBook.id) } : {}),
      ...(currentUser?.uuid ? { userUuid: currentUser.uuid } : {}),
      ...(currentUser?.id ? { userId: Number(currentUser.id) } : {})
    };

    const res = await fetchApi('/borrow', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
    if (res.ok) {
      showToast(`Successfully checked out "${bookTitle || matchedBook?.title || 'book'}"!`, 'success');
      if (currentPage === 'borrow') {
        loadBorrows();
      } else {
        setCurrentPage('borrow');
      }
    } else {
      showToast(res.data?.message || 'Failed to borrow book', 'error');
    }
  };

  const handleReturnBook = async (borrowId) => {
    if (!borrowId) return;
    const res = await fetchApi(`/borrow/${borrowId}`, { method: 'PATCH' });
    if (res.ok) {
      showToast('Book returned successfully', 'success');
      loadBorrows();
      refreshUserProfile();
    } else {
      showToast(res.data?.message || 'Failed to return book', 'error');
    }
  };

  // Fines Loader
  const loadFines = async () => {
    if (userRole === 'ADMIN') {
      if (allMembersForFines.length === 0) {
        const resM = await fetchApi('/user?page=0&size=100');
        if (resM.ok && resM.data) setAllMembersForFines(resM.data.content || resM.data || []);
      }

      let url = '/fines';
      if (selectedFineMemberId && selectedFineMemberId !== 'null') {
        url = `/fines/user/${selectedFineMemberId}`;
      }
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
    if (!fineId) return;
    const res = await fetchApi(`/fines/${fineId}/pay`, { method: 'POST' });
    if (res.ok) {
      showToast('Fine payment processed successfully', 'success');
      loadFines();
    } else {
      showToast(res.data?.message || 'Failed to settle fine', 'error');
    }
  };

  // Membership Loader - Loads status and optionally agreement text when on membership page
  const loadMembership = async (loadAgreement = false) => {
    const res = await fetchApi('/memberships/me');
    if (res.ok && res.data) {
      setMembership(res.data);
      const memUuid = res.data.uuid || res.data.membershipUuid || res.data.id;
      if (loadAgreement && memUuid && memUuid !== 'null' && res.data.status === 'PENDING') {
        const resTerms = await fetchApi(`/memberships/${memUuid}/agreement`);
        if (resTerms.ok && resTerms.data) {
          setAgreementText(typeof resTerms.data === 'string' ? resTerms.data : resTerms.data.terms || resTerms.data.agreementHtml || 'Athenaeum Library Membership Agreement...');
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
      if (res.data.agreementHtml) {
        setAgreementText(res.data.agreementHtml);
      }
      showToast('Membership application created. Please review and sign terms.', 'info');
      await loadMembership(true);
    } else {
      showToast(res.data?.message || 'Failed to apply for membership', 'error');
    }
  };

  const handleSignatureSubmit = async () => {
    const memUuid = membership?.uuid || membership?.membershipUuid;
    if (!sigFile || !membership || !memUuid || memUuid === 'null') {
      showToast('Please select a signature PNG image', 'error');
      return;
    }
    const formData = new FormData();
    formData.append('file', sigFile);
    const res = await fetchApi(`/memberships/${memUuid}/sign`, {
      method: 'POST',
      body: formData
    });
    if (res.ok && res.data) {
      setMembership(res.data);
      showToast('Membership signed & activated successfully!', 'success');
      setSigFile(null);
      setSigPreviewUrl('');
      await loadMembership(true);
    } else {
      showToast(res.data?.message || 'Failed to activate membership', 'error');
    }
  };

  const handleDownloadPdf = async () => {
    const memId = membership?.membershipId || membership?.id;
    if (!membership || !memId || memId === 'null') {
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
          loadMembership(false);
        } else {
          showToast(res.data?.message || 'Failed to cancel membership', 'error');
        }
      }
    });
    setActiveModal('confirm');
  };

  const handleOpenEditProfile = () => {
    if (currentUser) {
      const parsed = parsePhoneNumber(currentUser.phone || '');
      setProfileForm({
        name: currentUser.name || '',
        email: currentUser.email || '',
        countryCode: parsed.countryDial,
        phoneNumber: parsed.localNumber,
        phoneError: ''
      });
      setShowUserDropdown(false);
      setActiveModal('editProfile');
    }
  };

  const handleEditProfileSubmit = async (e) => {
    e.preventDefault();
    if (!profileForm.name.trim()) {
      showToast('Username / Name is required', 'error');
      return;
    }

    let formattedPhone = null;
    if (profileForm.phoneNumber && profileForm.phoneNumber.trim()) {
      const validation = validatePhoneNumber(profileForm.countryCode, profileForm.phoneNumber);
      if (!validation.isValid) {
        setProfileForm(prev => ({ ...prev, phoneError: validation.error }));
        return;
      }
      formattedPhone = `${profileForm.countryCode}${validation.cleanDigits}`;
    }

    const payload = {
      name: profileForm.name.trim(),
      email: profileForm.email.trim() || (currentUser ? currentUser.email : ''),
      phone: formattedPhone
    };

    const endpoint = (currentUser && currentUser.id) ? `/user/${currentUser.id}` : '/user/me';

    const res = await fetchApi(endpoint, {
      method: 'PUT',
      body: JSON.stringify(payload)
    });

    if (res.ok && res.data) {
      setCurrentUser(res.data);
      showToast('Profile updated successfully', 'success');
      setActiveModal(null);
    } else {
      const errMsg = res.data?.message || 'Failed to update profile';
      if (errMsg.toLowerCase().includes('phone') || errMsg.toLowerCase().includes('mobile')) {
        setProfileForm(prev => ({ ...prev, phoneError: errMsg }));
      }
      showToast(errMsg, 'error');
    }
  };

  const handlePromptPhoneSubmit = async (e) => {
    e.preventDefault();
    const validation = validatePhoneNumber(promptPhoneForm.countryCode, promptPhoneForm.phoneNumber);
    if (!validation.isValid) {
      setPromptPhoneForm(prev => ({ ...prev, phoneError: validation.error }));
      return;
    }

    const formattedPhone = `${promptPhoneForm.countryCode}${validation.cleanDigits}`;
    setPromptPhoneForm(prev => ({ ...prev, isSubmitting: true, phoneError: '' }));

    const payload = {
      name: currentUser?.name || 'User',
      email: currentUser?.email || '',
      phone: formattedPhone
    };

    const endpoint = (currentUser && currentUser.id) ? `/user/${currentUser.id}` : '/user/me';
    const res = await fetchApi(endpoint, {
      method: 'PUT',
      body: JSON.stringify(payload)
    });

    setPromptPhoneForm(prev => ({ ...prev, isSubmitting: false }));

    if (res.ok && res.data) {
      setCurrentUser(res.data);
      sessionStorage.setItem('phone_prompt_dismissed', 'true');
      showToast('Mobile number verified and saved successfully', 'success');
      setActiveModal(null);
    } else {
      const errMsg = res.data?.message || 'Failed to save phone number';
      setPromptPhoneForm(prev => ({ ...prev, phoneError: errMsg }));
      showToast(errMsg, 'error');
    }
  };

  // Render loading screen while Auth0 verifies session
  if (isLoading) {
    return (
      <div className="auth-screen">
        <div className="auth-card" style={{ textAlign: 'center', padding: '48px 32px' }}>
          <div className="auth-brand" style={{ justifyContent: 'center', marginBottom: '24px' }}>
            <div className="brand-icon">
              <BookOpen size={28} />
            </div>
            <h1 className="brand-title">Padips</h1>
          </div>
          <RefreshCw className="animate-spin mb-4" size={32} style={{ margin: '0 auto', color: '#6366f1' }} />
          <h3 style={{ fontSize: '18px', fontWeight: 600, marginBottom: '8px' }}>Authenticating session...</h3>
          <p style={{ color: '#888', fontSize: '14px' }}>Connecting to Auth0 Identity Provider</p>
        </div>
      </div>
    );
  }

  // Render Auth0 Login / Signup Screen if not authenticated
  if (!isAuthenticated) {
    const handleSignupSubmit = (e) => {
      if (e) e.preventDefault();
      if (!signupUsername.trim()) {
        setSignupError('Please enter a username');
        return;
      }
      setSignupError('');
      sessionStorage.setItem('auth0_pending_username', signupUsername.trim());
      loginWithRedirect({
        authorizationParams: {
          screen_hint: 'signup'
        }
      });
    };

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

          <div style={{ display: 'flex', borderBottom: '1px solid var(--border-subtle)', marginBottom: '20px' }}>
            <button
              type="button"
              style={{
                flex: 1,
                padding: '10px',
                background: 'none',
                border: 'none',
                borderBottom: authMode === 'signin' ? '2px solid var(--accent-primary)' : '2px solid transparent',
                color: authMode === 'signin' ? 'var(--text-main)' : 'var(--text-muted)',
                fontWeight: authMode === 'signin' ? 600 : 500,
                cursor: 'pointer',
                fontSize: '14px'
              }}
              onClick={() => { setAuthMode('signin'); setSignupError(''); }}
            >
              Sign In
            </button>
            <button
              type="button"
              style={{
                flex: 1,
                padding: '10px',
                background: 'none',
                border: 'none',
                borderBottom: authMode === 'signup' ? '2px solid var(--accent-primary)' : '2px solid transparent',
                color: authMode === 'signup' ? 'var(--text-main)' : 'var(--text-muted)',
                fontWeight: authMode === 'signup' ? 600 : 500,
                cursor: 'pointer',
                fontSize: '14px'
              }}
              onClick={() => { setAuthMode('signup'); setSignupError(''); }}
            >
              Register (Sign Up)
            </button>
          </div>

          <div className="auth-header">
            <h2>{authMode === 'signin' ? 'Sign in to portal' : 'Create your account'}</h2>
            <p className="text-muted" style={{ fontSize: '13px' }}>
              {authMode === 'signin'
                ? 'Access your library cards, book catalog, and more'
                : 'Choose a username and register'}
            </p>
          </div>

          {auth0Error && (
            <div className="alert alert-danger mb-4">
              {auth0Error.message || 'Authentication error occurred'}
            </div>
          )}

          {signupError && (
            <div className="alert alert-danger mb-4" style={{ padding: '8px 12px', fontSize: '13px' }}>
              {signupError}
            </div>
          )}

          {authMode === 'signin' ? (
            <div>
              <button
                type="button"
                className="btn btn-primary btn-block btn-lg mb-3"
                onClick={() => loginWithRedirect()}
              >
                <span className="btn-text">Sign In</span>
              </button>
              <div style={{ textAlign: 'center', marginTop: '16px' }}>
                <span style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
                  Don't have an account?{' '}
                  <button
                    type="button"
                    style={{ background: 'none', border: 'none', color: 'var(--accent-primary)', cursor: 'pointer', textDecoration: 'underline', padding: 0 }}
                    onClick={() => { setAuthMode('signup'); setSignupError(''); }}
                  >
                    Register
                  </button>
                </span>
              </div>
            </div>
          ) : (
            <form onSubmit={handleSignupSubmit}>
              <div className="form-group mb-4" style={{ textAlign: 'left' }}>
                <label className="form-label" style={{ display: 'block', marginBottom: '6px', fontSize: '13px', fontWeight: 500 }}>
                  Username <span style={{ color: 'var(--accent-primary)' }}>*</span>
                </label>
                <input
                  type="text"
                  className="form-input"
                  style={{
                    width: '100%',
                    padding: '10px 14px',
                    borderRadius: 'var(--radius-md)',
                    border: '1px solid var(--border-medium)',
                    background: 'var(--bg-input)',
                    color: 'var(--text-main)',
                    fontSize: '14px'
                  }}
                  placeholder="e.g. thani"
                  value={signupUsername}
                  onChange={(e) => setSignupUsername(e.target.value)}
                  autoFocus
                  required
                />
              </div>

              <button
                type="submit"
                className="btn btn-primary btn-block btn-lg mb-3"
              >
                <span className="btn-text">Continue to Sign Up</span>
              </button>

              <div style={{ textAlign: 'center', marginTop: '16px' }}>
                <span style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
                  Already have an account?{' '}
                  <button
                    type="button"
                    style={{ background: 'none', border: 'none', color: 'var(--accent-primary)', cursor: 'pointer', textDecoration: 'underline', padding: 0 }}
                    onClick={() => { setAuthMode('signin'); setSignupError(''); }}
                  >
                    Sign In
                  </button>
                </span>
              </div>
            </form>
          )}

          <div className="auth-footer" style={{ marginTop: '24px', textAlign: 'center' }}>
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

          <button
            className={`nav-link ${currentPage === 'donations' ? 'active' : ''}`}
            onClick={() => setCurrentPage('donations')}
          >
            <Gift className="nav-icon" size={18} />
            <span>{userRole === 'ADMIN' ? 'Book Donations' : 'Donate Books'}</span>
          </button>
        </nav>

        <div className="sidebar-profile">
          <div className="user-avatar" style={{ cursor: 'pointer' }} onClick={handleOpenEditProfile} title="Edit Profile">
            {monogram}
          </div>
          <div className="user-details" style={{ cursor: 'pointer' }} onClick={handleOpenEditProfile} title="Edit Profile">
            <span className="user-name">{userName}</span>
            <span className="user-role">{userRole === 'ADMIN' ? 'Librarian (Admin)' : 'Member'}</span>
          </div>
          <button className="btn-signout-icon" onClick={handleOpenEditProfile} title="Edit Profile" style={{ marginRight: '4px' }}>
            <Edit2 size={16} />
          </button>
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
              {currentPage === 'donations' && (userRole === 'ADMIN' ? 'Donation Management' : 'Book Donations')}
            </h1>
            <p className="header-page-subtitle">
              {currentPage === 'donations'
                ? (userRole === 'ADMIN' ? 'Review, approve, or reject user book donation requests' : 'Donate books to expand the library collection and view your donation history')
                : 'Overview of library holdings and staff operations'}
            </p>
          </div>

          <div className="header-actions">
            {userRole !== 'ADMIN' && (
              <div className="reward-points-badge" style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', background: 'rgba(234, 179, 8, 0.15)', color: '#eab308', border: '1px solid rgba(234, 179, 8, 0.3)', padding: '6px 12px', borderRadius: '20px', fontWeight: '600', fontSize: '13px' }} title="Your Reward Points">
                <Award size={16} />
                <span>{currentUser?.rewardPoints || 0} pts</span>
              </div>
            )}

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
                  <button className="dropdown-item" onClick={handleOpenEditProfile} style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <UserIcon size={14} />
                    <span>Edit Profile</span>
                  </button>
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
                      <button className="quick-action-btn" onClick={() => { setBookForm({ id: '', title: '', author: '', isbn: '', totalBookCount: 1 }); setActiveModal('createBook'); }}>
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
                  <div className="user-welcome-banner mb-6" style={{ background: 'linear-gradient(135deg, rgba(224, 122, 73, 0.12) 0%, rgba(224, 122, 73, 0.02) 100%)', border: '10px solid #121214', borderRadius: 'var(--radius-lg)', padding: '24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
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
                  {recentBooks.map((b, idx) => {
                    const total = b.totalBookCount ?? b.total_book_count ?? 0;
                    const borrowed = b.borrowedBookCount ?? b.borrowed_book_count ?? 0;
                    const avail = b.availableBookCount !== undefined ? b.availableBookCount : Math.max(0, total - borrowed);
                    return (
                      <div className="book-card" key={b.id ?? b.uuid ?? b.isbn ?? `recent-${idx}`}>
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
                          <div style={{ marginTop: '6px', fontSize: '12px' }}>
                            <span style={{ color: avail > 0 ? '#10B981' : '#EF4444', fontWeight: '600' }}>
                              {avail > 0 ? `Available: ${avail} ${avail === 1 ? 'copy' : 'copies'}` : 'Out of stock'}
                            </span>
                          </div>
                        </div>
                      </div>
                    );
                  })}
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
                  <button className="btn btn-primary" onClick={() => { setBookForm({ id: '', title: '', author: '', isbn: '', totalBookCount: 1, coverFile: null, coverPreview: '', existingCoverUrl: '' }); setActiveModal('createBook'); }}>
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
                      {userRole === 'ADMIN' ? (
                        <>
                          <th width="90">Total</th>
                          <th width="90">Borrowed</th>
                          <th width="100">Available</th>
                        </>
                      ) : (
                        <th width="140">Available Copies</th>
                      )}
                      <th width="180" className="text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {books.length === 0 ? (
                      <tr><td colSpan={userRole === 'ADMIN' ? 8 : 6} className="empty-cell">No books found in catalog.</td></tr>
                    ) : (
                      books.map((b, idx) => {
                        const total = b.totalBookCount ?? b.total_book_count ?? 0;
                        const borrowed = b.borrowedBookCount ?? b.borrowed_book_count ?? 0;
                        const avail = b.availableBookCount !== undefined ? b.availableBookCount : Math.max(0, total - borrowed);
                        return (
                          <tr key={b.id ?? b.uuid ?? b.isbn ?? `book-${idx}`}>
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
                            {userRole === 'ADMIN' ? (
                              <>
                                <td><span className="text-muted" style={{ fontWeight: 500 }}>{total}</span></td>
                                <td><span style={{ color: '#f59e0b', fontWeight: 600 }}>{borrowed}</span></td>
                                <td><span style={{ color: avail > 0 ? '#10b981' : '#ef4444', fontWeight: 600 }}>{avail} left</span></td>
                              </>
                            ) : (
                              <td>
                                <span style={{ color: avail > 0 ? '#10b981' : '#ef4444', fontWeight: 600 }}>
                                  {avail > 0 ? `${avail} available` : 'Out of stock'}
                                </span>
                              </td>
                            )}
                            <td className="text-right">
                              {userRole === 'ADMIN' ? (
                                <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
                                  <button className="btn btn-secondary btn-sm" onClick={() => {
                                    setBookForm({
                                      id: b.id,
                                      title: b.title,
                                      author: b.author,
                                      isbn: b.isbn,
                                      totalBookCount: b.totalBookCount ?? b.total_book_count ?? 1,
                                      coverFile: null,
                                      coverPreview: '',
                                      existingCoverUrl: b.coverImageUrl || ''
                                    });
                                    setActiveModal('editBook');
                                  }}>Edit</button>
                                  <button className="btn btn-danger btn-sm" onClick={() => handleDeleteBook(b.id, b.title)}>Del</button>
                                </div>
                              ) : (
                                avail <= 0 ? (
                                  <button className="btn btn-secondary btn-sm" disabled style={{ opacity: 0.5, cursor: 'not-allowed' }}>Out of Stock</button>
                                ) : (
                                  <button className="btn btn-primary btn-sm" onClick={() => handleDirectBorrow(b.id ?? b.uuid, b.title)}>Borrow</button>
                                )
                              )}
                            </td>
                          </tr>
                        );
                      })
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
                  <button className="btn btn-secondary" onClick={() => { setMembersPage(0); loadMembers(0, memberSearchQuery, memberSortBy, memberSortDir); }}>Search</button>
                  <button className="btn btn-ghost" onClick={() => { setMemberSearchQuery(''); loadMembers(0, '', 'id', 'asc'); }}>Clear</button>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginLeft: '12px' }}>
                    <span className="text-xs text-muted font-medium">Sort by:</span>
                    <select
                      className="form-select"
                      style={{ padding: '6px 12px', fontSize: '13px' }}
                      value={memberSortBy}
                      onChange={(e) => { setMemberSortBy(e.target.value); loadMembers(0, memberSearchQuery, e.target.value, memberSortDir); }}
                    >
                      <option value="id">ID</option>
                      <option value="name">Name</option>
                      <option value="email">Email</option>
                      <option value="rewardPoints">Reward Points</option>
                    </select>
                    <select
                      className="form-select"
                      style={{ padding: '6px 12px', fontSize: '13px' }}
                      value={memberSortDir}
                      onChange={(e) => { setMemberSortDir(e.target.value); loadMembers(0, memberSearchQuery, memberSortBy, e.target.value); }}
                    >
                      <option value="asc">Ascending ⬆</option>
                      <option value="desc">Descending ⬇</option>
                    </select>
                  </div>
                </div>

                <button className="btn btn-primary" onClick={() => { setUserForm({ id: '', name: '', email: '', password: '', countryCode: '+1', phoneNumber: '', phoneError: '' }); setActiveModal('createUser'); }}>
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
                      <th width="150">Phone Number</th>
                      <th width="130">Reward Points</th>
                      <th width="170" className="text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {members.length === 0 ? (
                      <tr><td colSpan="6" className="empty-cell">No members found.</td></tr>
                    ) : (
                      members.map((m, idx) => (
                        <tr key={m.id ?? m.uuid ?? m.email ?? `member-${idx}`}>
                          <td>#{m.id || '—'}</td>
                          <td><strong>{m.name || m.username}</strong></td>
                          <td>{m.email}</td>
                          <td>
                            {m.phone ? (
                              <span style={{ display: 'inline-flex', alignItems: 'center', gap: '5px', fontSize: '13px', fontWeight: '500' }}>
                                <Phone size={13} style={{ color: 'var(--accent-primary)', opacity: 0.8 }} />
                                <code>{m.phone}</code>
                              </span>
                            ) : (
                              <span className="text-subtle" style={{ fontSize: '12px' }}>—</span>
                            )}
                          </td>
                          <td>
                            <span style={{ color: '#eab308', fontWeight: '600', fontSize: '13px' }}>
                              🏆 {m.rewardPoints || 0} pts
                            </span>
                          </td>
                          <td className="text-right">
                            <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
                              <button className="btn btn-secondary btn-sm" onClick={() => {
                                const parsed = parsePhoneNumber(m.phone || '');
                                setUserForm({
                                  id: m.id,
                                  name: m.name || m.username,
                                  email: m.email,
                                  password: '',
                                  countryCode: parsed.countryDial,
                                  phoneNumber: parsed.localNumber,
                                  phoneError: ''
                                });
                                setActiveModal('editUser');
                              }}>Edit</button>
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
                    <button className="btn btn-primary btn-sm" onClick={handleOpenAdminBorrowModal}>
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
                          adminBorrows.map((b, idx) => {
                            const title = b.bookTitle || b.book?.title || 'Untitled Book';
                            const author = b.bookAuthor || b.book?.author || 'Unknown Author';
                            const cover = b.bookCoverImageUrl || b.book?.coverImageUrl;
                            const borrowerName = b.userName || b.userEmail || b.user?.name || b.user?.email || `User #${b.userId || b.userNumericId}`;
                            const isReturned = b.status === 'RETURNED' || !!b.returnedDate || !!b.returnDate;
                            return (
                              <tr key={b.id ?? b.uuid ?? `borrow-admin-${idx}`}>
                                <td>
                                  {cover ? (
                                    <img src={getCoverUrl(cover)} alt="Cover" className="table-thumb-img" />
                                  ) : (
                                    <div className="table-thumb">{getMonogram(title)}</div>
                                  )}
                                </td>
                                <td><strong>{title}</strong><br /><span className="text-muted">{author}</span></td>
                                <td>{borrowerName}</td>
                                <td>{b.borrowDate}</td>
                                <td>{b.dueDate}</td>
                                <td>
                                  <span style={{ color: isReturned ? '#10b981' : '#f59e0b', fontWeight: 600 }}>
                                    {isReturned ? 'Returned' : 'Active'}
                                  </span>
                                </td>
                                <td className="text-right">
                                  {!isReturned && (
                                    <button className="btn btn-secondary btn-sm" onClick={() => handleReturnBook(b.id)}>Return</button>
                                  )}
                                </td>
                              </tr>
                            );
                          })
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
                      <button className="btn btn-primary btn-sm" onClick={handleOpenUserBorrowModal}>+ Borrow a Book</button>
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
                          userBorrows.map((b, idx) => {
                            const title = b.bookTitle || b.book?.title || 'Untitled Book';
                            const author = b.bookAuthor || b.book?.author || 'Unknown Author';
                            const cover = b.bookCoverImageUrl || b.book?.coverImageUrl;
                            const isReturned = b.status === 'RETURNED' || !!b.returnedDate || !!b.returnDate;
                            return (
                              <tr key={b.id ?? b.uuid ?? `borrow-user-${idx}`}>
                                <td>
                                  {cover ? (
                                    <img src={getCoverUrl(cover)} alt="Cover" className="table-thumb-img" />
                                  ) : (
                                    <div className="table-thumb">{getMonogram(title)}</div>
                                  )}
                                </td>
                                <td><strong>{title}</strong></td>
                                <td>{author}</td>
                                <td>{b.borrowDate}</td>
                                <td>{b.dueDate}</td>
                                <td>
                                  <span style={{ color: isReturned ? '#10b981' : '#f59e0b', fontWeight: 600 }}>
                                    {isReturned ? 'Returned' : 'Active'}
                                  </span>
                                </td>
                                <td className="text-right">
                                  {!isReturned && (
                                    <button className="btn btn-secondary btn-sm" onClick={() => handleReturnBook(b.id)}>Check In</button>
                                  )}
                                </td>
                              </tr>
                            );
                          })
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
                        {allMembersForFines.map((m, idx) => (
                          <option key={m.id ?? m.uuid ?? m.email ?? `fine-m-${idx}`} value={m.id || ''}>{m.name || m.email} (#{m.id || '—'})</option>
                        ))}
                      </select>
                    </div>
                  </div>
                )}

                <div className="fine-summary-banner mt-6">
                  <div className="summary-item">
                    <span className="summary-label">Account / Scope:</span>
                    <span className="summary-value">{userRole === 'ADMIN' ? (selectedFineMemberId ? `Member #${selectedFineMemberId}` : 'All Members') : (currentUser?.name || currentUser?.email || 'Member')}</span>
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
                        fines.map((f, idx) => {
                          const fineUser = f.userName || f.userEmail || f.user?.name || f.user?.email || `User #${f.userId || f.userNumericId}`;
                          const fineTitle = f.bookTitle || f.borrow?.book?.title || 'Library Title';
                          const fineAuthor = f.bookAuthor || f.borrow?.book?.author || '';
                          return (
                            <tr key={f.id ?? f.uuid ?? `fine-row-${idx}`}>
                              <td>#{f.id || '—'}</td>
                              {userRole === 'ADMIN' && <td>{fineUser}</td>}
                              <td><strong>{fineTitle}</strong>{fineAuthor ? <><br /><span className="text-muted">{fineAuthor}</span></> : null}</td>
                              <td><strong>${(f.amount || f.pendingFineAmount || 0).toFixed(2)}</strong></td>
                              <td>
                                <span style={{ color: f.status === 'PAID' ? '#10b981' : '#ef4444', fontWeight: 600 }}>
                                  {f.status}
                                </span>
                              </td>
                              <td className="text-right">
                                {f.status === 'UNPAID' || f.status === 'PENDING' ? (
                                  <button className="btn btn-primary btn-sm" onClick={() => handlePayFine(f.id)}>Settle & Pay</button>
                                ) : null}
                              </td>
                            </tr>
                          );
                        })
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
                    <div
                      className="panel-agreement p-4 rounded-lg"
                      style={{ background: '#ffffff', color: '#0f172a', border: '1px solid var(--border-color)', maxHeight: '450px', overflowY: 'auto' }}
                      dangerouslySetInnerHTML={{ __html: agreementText || '<div style="padding: 20px; color: #475569;">Loading membership agreement template...</div>' }}
                    />

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
                    <div className="membership-card-badge" style={{ background: 'linear-gradient(135deg, #1e293b 0%, #0f172a 100%)', border: '1px solid #334155', borderRadius: '16px', padding: '24px', color: '#ffffff', minHeight: '220px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between', position: 'relative', overflow: 'hidden' }}>
                      <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                        <div>
                          <h4 style={{ fontSize: '18px', fontWeight: 'bold', letterSpacing: '0.05em', color: '#38bdf8', margin: 0 }}>ATHENAEUM</h4>
                          <span style={{ fontSize: '10px', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.1em' }}>Library System</span>
                        </div>
                        <span style={{ color: '#4ade80', fontWeight: 700, fontSize: '12px', letterSpacing: '0.05em' }}>ACTIVE</span>
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

          {/* PAGE 6: BOOK DONATIONS */}
          {currentPage === 'donations' && (
            <div className="donation-page-container">
              {userRole === 'ADMIN' ? (
                <div>
                  {/* Admin Stats Grid */}
                  <div className="donation-stats-row mb-6">
                    <div className="donation-stat-box">
                      <div className="donation-stat-icon" style={{ background: 'rgba(99, 102, 241, 0.15)', color: '#818cf8' }}>
                        <Gift size={22} />
                      </div>
                      <div>
                        <div className="donation-stat-label">Total Submissions</div>
                        <div className="donation-stat-value">{donations.length}</div>
                      </div>
                    </div>
                    <div className="donation-stat-box">
                      <div className="donation-stat-icon" style={{ background: 'rgba(234, 179, 8, 0.15)', color: '#eab308' }}>
                        <RefreshCw size={20} />
                      </div>
                      <div>
                        <div className="donation-stat-label">Pending Review</div>
                        <div className="donation-stat-value">{donations.filter(d => d.status === 'PENDING').length}</div>
                      </div>
                    </div>
                    <div className="donation-stat-box">
                      <div className="donation-stat-icon" style={{ background: 'rgba(16, 185, 129, 0.15)', color: '#10b981' }}>
                        <BookOpen size={20} />
                      </div>
                      <div>
                        <div className="donation-stat-label">Approved Books</div>
                        <div className="donation-stat-value">
                          {donations.filter(d => d.status === 'APPROVED').reduce((sum, d) => sum + (d.donatedBookCount || 1), 0)}
                        </div>
                      </div>
                    </div>
                    <div className="donation-stat-box">
                      <div className="donation-stat-icon" style={{ background: 'rgba(239, 68, 68, 0.15)', color: '#ef4444' }}>
                        <X size={20} />
                      </div>
                      <div>
                        <div className="donation-stat-label">Rejected</div>
                        <div className="donation-stat-value">{donations.filter(d => d.status === 'REJECTED').length}</div>
                      </div>
                    </div>
                  </div>

                  <div className="action-bar mb-4" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
                    <div className="filter-controls" style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                      <span className="text-xs text-muted font-medium mr-1">Filter:</span>
                      {['ALL', 'PENDING', 'APPROVED', 'REJECTED'].map(st => {
                        const count = st === 'ALL'
                          ? donations.length
                          : donations.filter(d => d.status === st).length;
                        return (
                          <button
                            key={st}
                            className={`btn btn-sm ${donationFilter === st ? 'btn-primary' : 'btn-secondary'}`}
                            onClick={() => setDonationFilter(st)}
                            style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                          >
                            <span>{st}</span>
                            <span style={{
                              background: donationFilter === st ? 'rgba(255,255,255,0.25)' : 'rgba(255,255,255,0.1)',
                              padding: '1px 6px',
                              borderRadius: '10px',
                              fontSize: '11px',
                              fontWeight: '600'
                            }}>
                              {count}
                            </span>
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  <div className="table-container mt-4">
                    <table className="data-table">
                      <thead>
                        <tr>
                          <th width="60">ID</th>
                          <th width="70">Cover</th>
                          <th>Book Details</th>
                          <th>Donor Information</th>
                          <th>ISBN</th>
                          <th width="90">Copies</th>
                          <th width="120">Status</th>
                          <th width="120">Submitted</th>
                          <th width="200" className="text-right">Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {(() => {
                          const filtered = donationFilter === 'ALL'
                            ? donations
                            : donations.filter(d => d.status === donationFilter);
                          if (filtered.length === 0) {
                            return (
                              <tr>
                                <td colSpan="9" className="empty-cell" style={{ padding: '40px 20px', textAlign: 'center' }}>
                                  No donation requests found {donationFilter !== 'ALL' ? `with status "${donationFilter}"` : ''}.
                                </td>
                              </tr>
                            );
                          }
                          return filtered.map((d, idx) => (
                            <tr key={d.id ?? `donation-${idx}`}>
                              <td>#{d.id || '—'}</td>
                              <td>
                                {d.coverImageUrl ? (
                                  <img src={getCoverUrl(d.coverImageUrl)} alt={d.title} className="table-thumb-img" />
                                ) : (
                                  <div className="table-thumb">{getMonogram(d.title)}</div>
                                )}
                              </td>
                              <td>
                                <strong>{d.title}</strong>
                                <span className="text-muted" style={{ display: 'block', fontSize: '12px' }}>by {d.author}</span>
                              </td>
                              <td>
                                <span>{d.userName || 'Member'}</span>
                                <span className="text-muted" style={{ display: 'block', fontSize: '11px' }}>{d.userEmail}</span>
                              </td>
                              <td><code>{d.isbn}</code></td>
                              <td>
                                <span className="text-muted" style={{ fontWeight: 500 }}>
                                  {d.donatedBookCount} {d.donatedBookCount === 1 ? 'copy' : 'copies'}
                                </span>
                              </td>
                              <td>
                                {d.status === 'APPROVED' && <span style={{ color: '#10b981', fontWeight: 600 }}>Approved</span>}
                                {d.status === 'PENDING' && <span style={{ color: '#f59e0b', fontWeight: 600 }}>Pending Review</span>}
                                {d.status === 'REJECTED' && (
                                  <div>
                                    <span style={{ color: '#ef4444', fontWeight: 600 }}>Rejected</span>
                                    {d.rejectionReason && (
                                      <span className="text-muted" style={{ display: 'block', fontSize: '11px', marginTop: '2px' }} title={d.rejectionReason}>
                                        {d.rejectionReason.length > 25 ? d.rejectionReason.substring(0, 25) + '...' : d.rejectionReason}
                                      </span>
                                    )}
                                  </div>
                                )}
                              </td>
                              <td>
                                <span style={{ fontSize: '12px' }}>
                                  {d.createdAt ? new Date(d.createdAt).toLocaleDateString() : '—'}
                                </span>
                              </td>
                              <td className="text-right">
                                {d.status === 'PENDING' ? (
                                  <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                                    <button
                                      className="btn btn-primary btn-sm"
                                      onClick={() => handleApproveDonation(d.id)}
                                      title="Approve and add to catalog"
                                    >
                                      Approve
                                    </button>
                                    <button
                                      className="btn btn-danger btn-sm"
                                      onClick={() => setRejectionModal({ open: true, donationId: d.id, reason: '' })}
                                      title="Reject donation"
                                    >
                                      Reject
                                    </button>
                                  </div>
                                ) : (
                                  <span className="text-muted" style={{ fontSize: '12px' }}>Reviewed</span>
                                )}
                              </td>
                            </tr>
                          ));
                        })()}
                      </tbody>
                    </table>
                  </div>
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                  {/* Hero Banner */}
                  <div className="donation-hero-card">
                    <div className="donation-hero-content">
                      <span style={{ color: 'var(--accent-primary)', fontWeight: 700, fontSize: '11px', letterSpacing: '0.06em', textTransform: 'uppercase' }}>
                        COMMUNITY DONATION PROGRAM
                      </span>
                      <h2 className="donation-hero-title">Donate Books to Padips Library</h2>
                      <p className="donation-hero-desc">
                        Share knowledge and help expand our collection. Submit book details with an optional cover image; our librarians will review and catalog your copies. Earn <strong>2 reward points</strong> for each approved copy!
                      </p>
                    </div>
                    <div>
                      <button
                        className="btn btn-primary btn-lg"
                        style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', padding: '10px 20px', borderRadius: 'var(--radius-sm)' }}
                        onClick={() => {
                          setDonationForm({ title: '', author: '', isbn: '', donatedBookCount: 1, coverFile: null, coverPreview: '' });
                          setActiveModal('createDonation');
                        }}
                      >
                        <Plus size={18} />
                        <span>Donate a Book</span>
                      </button>
                    </div>
                  </div>

                  {/* Donor Stats Grid */}
                  <div className="donation-stats-row">
                    <div className="donation-stat-box">
                      <div className="donation-stat-icon" style={{ background: 'rgba(224, 122, 73, 0.12)', color: 'var(--accent-primary)' }}>
                        <Gift size={20} />
                      </div>
                      <div>
                        <div className="donation-stat-label">My Donations</div>
                        <div className="donation-stat-value">{donations.length}</div>
                      </div>
                    </div>
                    <div className="donation-stat-box">
                      <div className="donation-stat-icon" style={{ background: 'rgba(16, 185, 129, 0.12)', color: '#10b981' }}>
                        <BookOpen size={18} />
                      </div>
                      <div>
                        <div className="donation-stat-label">Copies Approved</div>
                        <div className="donation-stat-value">
                          {donations.filter(d => d.status === 'APPROVED').reduce((sum, d) => sum + (d.donatedBookCount || 1), 0)}
                        </div>
                      </div>
                    </div>
                    <div className="donation-stat-box">
                      <div className="donation-stat-icon" style={{ background: 'rgba(234, 179, 8, 0.12)', color: '#eab308' }}>
                        <RefreshCw size={18} />
                      </div>
                      <div>
                        <div className="donation-stat-label">Pending Review</div>
                        <div className="donation-stat-value">{donations.filter(d => d.status === 'PENDING').length}</div>
                      </div>
                    </div>
                    <div className="donation-stat-box">
                      <div className="donation-stat-icon" style={{ background: 'rgba(234, 179, 8, 0.12)', color: '#fbbf24' }}>
                        <Award size={20} />
                      </div>
                      <div>
                        <div className="donation-stat-label">Reward Points</div>
                        <div className="donation-stat-value" style={{ color: '#fbbf24' }}>
                          {currentUser?.rewardPoints || 0} <span style={{ fontSize: '13px', fontWeight: '500', color: 'var(--text-subtle)' }}>pts</span>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="section-header" style={{ marginBottom: '8px' }}>
                    <h3>My Donation History</h3>
                    <p className="section-desc">Track status and review outcomes of your donated books</p>
                  </div>

                  {donations.length === 0 ? (
                    <div className="donation-empty-state">
                      <div className="donation-empty-icon">
                        <Gift size={28} />
                      </div>
                      <h4 className="donation-empty-title">No Book Donations Yet</h4>
                      <p className="donation-empty-desc">
                        Help our community grow by donating books you have loved. Our librarians will review and add them to the library catalog.
                      </p>
                      <button
                        className="btn btn-primary"
                        style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}
                        onClick={() => {
                          setDonationForm({ title: '', author: '', isbn: '', donatedBookCount: 1, coverFile: null, coverPreview: '' });
                          setActiveModal('createDonation');
                        }}
                      >
                        <Plus size={16} />
                        <span>Submit Your First Donation</span>
                      </button>
                    </div>
                  ) : (
                    <div className="table-container">
                      <table className="data-table">
                        <thead>
                          <tr>
                            <th width="60">ID</th>
                            <th width="70">Cover</th>
                            <th>Book Title & Author</th>
                            <th>ISBN</th>
                            <th width="90">Copies</th>
                            <th width="130">Status</th>
                            <th width="120">Submitted</th>
                            <th width="130" className="text-right">Actions</th>
                          </tr>
                        </thead>
                        <tbody>
                          {donations.map((d, idx) => (
                            <tr key={d.id ?? `my-don-${idx}`}>
                              <td>#{d.id || '—'}</td>
                              <td>
                                {d.coverImageUrl ? (
                                  <img src={getCoverUrl(d.coverImageUrl)} alt={d.title} className="table-thumb-img" />
                                ) : (
                                  <div className="table-thumb">{getMonogram(d.title)}</div>
                                )}
                              </td>
                              <td>
                                <strong>{d.title}</strong>
                                <span className="text-muted" style={{ display: 'block', fontSize: '12px' }}>by {d.author}</span>
                              </td>
                              <td><code>{d.isbn}</code></td>
                              <td>
                                <span className="text-muted" style={{ fontWeight: 500 }}>
                                  {d.donatedBookCount} {d.donatedBookCount === 1 ? 'copy' : 'copies'}
                                </span>
                              </td>
                              <td>
                                {d.status === 'APPROVED' && <span style={{ color: '#10b981', fontWeight: 600 }}>Approved</span>}
                                {d.status === 'PENDING' && <span style={{ color: '#f59e0b', fontWeight: 600 }}>Pending Review</span>}
                                {d.status === 'REJECTED' && (
                                  <div>
                                    <span style={{ color: '#ef4444', fontWeight: 600 }}>Rejected</span>
                                    {d.rejectionReason && (
                                      <span className="text-muted" style={{ display: 'block', fontSize: '11px', marginTop: '2px' }}>
                                        Reason: {d.rejectionReason}
                                      </span>
                                    )}
                                  </div>
                                )}
                              </td>
                              <td>
                                <span style={{ fontSize: '12px' }}>
                                  {d.createdAt ? new Date(d.createdAt).toLocaleDateString() : '—'}
                                </span>
                              </td>
                              <td className="text-right">
                                {d.status === 'PENDING' ? (
                                  <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
                                    <button
                                      className="btn btn-secondary btn-sm"
                                      onClick={() => handleOpenEditDonation(d)}
                                      title="Edit donation details"
                                    >
                                      Edit
                                    </button>
                                    <button
                                      className="btn btn-ghost btn-sm"
                                      onClick={() => handleDeleteDonation(d.id, d.title)}
                                      title="Delete donation"
                                      style={{ color: '#ef4444' }}
                                    >
                                      Delete
                                    </button>
                                  </div>
                                ) : (
                                  <span className="text-muted" style={{ fontSize: '12px' }}>—</span>
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </main>
      </div>

      {/* -1. Prompt Mobile Number Onboarding Modal */}
      {activeModal === 'missingPhonePrompt' && (
        <div className="modal-backdrop open">
          <div className="modal-card max-w-sm" style={{ padding: '28px 24px', borderRadius: '20px' }}>
            <div className="modal-header border-none" style={{ padding: 0, marginBottom: '20px', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <h3 style={{ fontSize: '20px', fontWeight: '700', marginBottom: '4px' }}>Add Mobile Number</h3>
                <p className="text-subtle" style={{ fontSize: '13px', margin: 0 }}>
                  Please add your phone number with country code to complete your profile.
                </p>
              </div>
              <button
                className="btn-close"
                onClick={() => {
                  sessionStorage.setItem('phone_prompt_dismissed', 'true');
                  setActiveModal(null);
                }}
              >
                &times;
              </button>
            </div>
            <form onSubmit={handlePromptPhoneSubmit}>
              <div className="modal-body" style={{ padding: 0 }}>
                <div className="form-group mb-4">
                  <label className="form-label" style={{ fontSize: '13px', fontWeight: '600', color: 'var(--text-subtle)', marginBottom: '8px' }}>
                    Phone number
                  </label>
                  <div className={`phone-input-control ${promptPhoneForm.phoneError ? 'has-error' : ''}`}>
                    <select
                      className="phone-country-select"
                      value={promptPhoneForm.countryCode}
                      onChange={e => setPromptPhoneForm({ ...promptPhoneForm, countryCode: e.target.value, phoneError: '' })}
                    >
                      {COUNTRY_CODES.map(c => (
                        <option key={`prompt-${c.code}-${c.dial}`} value={c.dial}>
                          {c.code} {c.dial}
                        </option>
                      ))}
                    </select>
                    <input
                      type="tel"
                      className="phone-number-field"
                      placeholder="555 555 5555"
                      required
                      value={promptPhoneForm.phoneNumber}
                      onChange={e => {
                        const val = e.target.value.replace(/[^\d\s-]/g, '');
                        setPromptPhoneForm(prev => {
                          const res = prev.phoneError ? validatePhoneNumber(prev.countryCode, val) : null;
                          return {
                            ...prev,
                            phoneNumber: val,
                            phoneError: (res && res.isValid) ? '' : prev.phoneError
                          };
                        });
                      }}
                      onBlur={() => {
                        if (promptPhoneForm.phoneNumber.trim()) {
                          const res = validatePhoneNumber(promptPhoneForm.countryCode, promptPhoneForm.phoneNumber);
                          setPromptPhoneForm(prev => ({ ...prev, phoneError: res.isValid ? '' : res.error }));
                        }
                      }}
                    />
                  </div>
                  {promptPhoneForm.phoneError && (
                    <small className="text-danger" style={{ display: 'block', marginTop: '6px', fontSize: '12px' }}>
                      {promptPhoneForm.phoneError}
                    </small>
                  )}
                </div>
              </div>
              <div className="modal-footer border-none" style={{ padding: '12px 0 0 0', display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <button
                  type="submit"
                  className="btn-continue-pill"
                  disabled={promptPhoneForm.isSubmitting || !promptPhoneForm.phoneNumber.trim()}
                >
                  {promptPhoneForm.isSubmitting ? (
                    <>
                      <RefreshCw className="animate-spin" size={16} />
                      <span>Saving...</span>
                    </>
                  ) : (
                    'Continue'
                  )}
                </button>
                <button
                  type="button"
                  className="btn btn-ghost btn-sm"
                  style={{ width: '100%', color: 'var(--text-subtle)' }}
                  onClick={() => {
                    sessionStorage.setItem('phone_prompt_dismissed', 'true');
                    setActiveModal(null);
                  }}
                >
                  Remind Me Later
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 0. Edit Profile Modal */}
      {activeModal === 'editProfile' && (
        <div className="modal-backdrop open">
          <div className="modal-card">
            <div className="modal-header">
              <h3>Edit Profile</h3>
              <button className="btn-close" onClick={() => setActiveModal(null)}>&times;</button>
            </div>
            <form onSubmit={handleEditProfileSubmit}>
              <div className="modal-body">
                <div className="form-group mb-4">
                  <label className="form-label">Username / Name <span style={{ color: 'var(--accent-primary)' }}>*</span></label>
                  <input
                    type="text"
                    className="form-input"
                    required
                    value={profileForm.name}
                    onChange={e => setProfileForm({ ...profileForm, name: e.target.value })}
                    placeholder="Enter your username"
                  />
                </div>
                <div className="form-group mb-4">
                  <label className="form-label">Email Address</label>
                  <input
                    type="email"
                    className="form-input"
                    value={profileForm.email}
                    onChange={e => setProfileForm({ ...profileForm, email: e.target.value })}
                    placeholder="user@example.com"
                  />
                  <small className="text-muted" style={{ display: 'block', marginTop: '4px', fontSize: '12px' }}>
                    Primary email address for notifications and account identifier.
                  </small>
                </div>
                <div className="form-group mb-4">
                  <label className="form-label">Phone number</label>
                  <div className={`phone-input-control ${profileForm.phoneError ? 'has-error' : ''}`}>
                    <select
                      className="phone-country-select"
                      value={profileForm.countryCode}
                      onChange={e => setProfileForm({ ...profileForm, countryCode: e.target.value, phoneError: '' })}
                    >
                      {COUNTRY_CODES.map(c => (
                        <option key={`profile-${c.code}-${c.dial}`} value={c.dial}>
                          {c.code} {c.dial}
                        </option>
                      ))}
                    </select>
                    <input
                      type="tel"
                      className="phone-number-field"
                      placeholder="555 555 5555"
                      value={profileForm.phoneNumber}
                      onChange={e => {
                        const val = e.target.value.replace(/[^\d\s-]/g, '');
                        setProfileForm(prev => {
                          const res = prev.phoneError ? validatePhoneNumber(prev.countryCode, val) : null;
                          return {
                            ...prev,
                            phoneNumber: val,
                            phoneError: (res && res.isValid) ? '' : prev.phoneError
                          };
                        });
                      }}
                      onBlur={() => {
                        if (profileForm.phoneNumber.trim()) {
                          const res = validatePhoneNumber(profileForm.countryCode, profileForm.phoneNumber);
                          setProfileForm(prev => ({ ...prev, phoneError: res.isValid ? '' : res.error }));
                        }
                      }}
                    />
                  </div>
                  {profileForm.phoneError ? (
                    <small className="text-danger" style={{ display: 'block', marginTop: '6px', fontSize: '12px' }}>
                      {profileForm.phoneError}
                    </small>
                  ) : (
                    <small className="text-muted" style={{ display: 'block', marginTop: '4px', fontSize: '12px' }}>
                      Select your country code and enter mobile digits (e.g. US +1 555 555 5555).
                    </small>
                  )}
                </div>
                <div className="form-group mb-4">
                  <label className="form-label">Account Role & Points</label>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '6px' }}>
                    <span className="badge badge-primary" style={{ padding: '6px 12px', fontSize: '12px' }}>
                      {currentUser?.role || userRole}
                    </span>
                    {currentUser?.rewardPoints !== undefined && (
                      <span className="badge badge-warning" style={{ padding: '6px 12px', fontSize: '12px' }}>
                        🏆 {currentUser.rewardPoints} Reward Points
                      </span>
                    )}
                  </div>
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
                <div className="form-group mb-4">
                  <label className="form-label">Book Title <span style={{ color: 'var(--accent-primary)' }}>*</span></label>
                  <input type="text" className="form-input" required value={bookForm.title} onChange={e => setBookForm({ ...bookForm, title: e.target.value })} placeholder="e.g. Design Patterns" />
                </div>
                <div className="form-group mb-4">
                  <label className="form-label">Author Name <span style={{ color: 'var(--accent-primary)' }}>*</span></label>
                  <input type="text" className="form-input" required value={bookForm.author} onChange={e => setBookForm({ ...bookForm, author: e.target.value })} placeholder="e.g. Erich Gamma" />
                </div>
                <div className="form-group mb-4">
                  <label className="form-label">ISBN Number <span style={{ color: 'var(--accent-primary)' }}>*</span></label>
                  <input type="text" className="form-input" required value={bookForm.isbn} onChange={e => setBookForm({ ...bookForm, isbn: e.target.value })} placeholder="e.g. 9780201633610" />
                </div>
                <div className="form-group mb-4">
                  <label className="form-label">Total Book Count (Copies) <span style={{ color: 'var(--accent-primary)' }}>*</span></label>
                  <input type="number" min="1" className="form-input" required value={bookForm.totalBookCount} onChange={e => setBookForm({ ...bookForm, totalBookCount: e.target.value })} />
                </div>
                <div className="form-group mb-2">
                  <label className="form-label">Book Cover Image (Optional)</label>
                  <input
                    type="file"
                    className="form-input"
                    accept="image/*"
                    onChange={e => {
                      if (e.target.files && e.target.files[0]) {
                        const f = e.target.files[0];
                        setBookForm(prev => ({ ...prev, coverFile: f, coverPreview: URL.createObjectURL(f) }));
                      }
                    }}
                  />
                  <small className="text-muted" style={{ display: 'block', marginTop: '4px', fontSize: '12px' }}>
                    Select a JPG or PNG cover image to attach directly with this title.
                  </small>
                </div>
                {bookForm.coverPreview && (
                  <div className="cover-preview-box mt-3" style={{ maxWidth: '140px', borderRadius: 'var(--radius-md)', overflow: 'hidden', border: '1px solid var(--border-color)' }}>
                    <img src={bookForm.coverPreview} alt="Cover Preview" style={{ width: '100%', height: 'auto', display: 'block' }} />
                  </div>
                )}
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
                <div className="form-group mb-4">
                  <label className="form-label">Book Title <span style={{ color: 'var(--accent-primary)' }}>*</span></label>
                  <input type="text" className="form-input" required value={bookForm.title} onChange={e => setBookForm({ ...bookForm, title: e.target.value })} />
                </div>
                <div className="form-group mb-4">
                  <label className="form-label">Author Name <span style={{ color: 'var(--accent-primary)' }}>*</span></label>
                  <input type="text" className="form-input" required value={bookForm.author} onChange={e => setBookForm({ ...bookForm, author: e.target.value })} />
                </div>
                <div className="form-group mb-4">
                  <label className="form-label">ISBN Number <span style={{ color: 'var(--accent-primary)' }}>*</span></label>
                  <input type="text" className="form-input" required value={bookForm.isbn} onChange={e => setBookForm({ ...bookForm, isbn: e.target.value })} />
                </div>
                <div className="form-group mb-4">
                  <label className="form-label">Total Book Count (Copies) <span style={{ color: 'var(--accent-primary)' }}>*</span></label>
                  <input type="number" min="1" className="form-input" required value={bookForm.totalBookCount} onChange={e => setBookForm({ ...bookForm, totalBookCount: e.target.value })} />
                </div>
                <div className="form-group mb-2">
                  <label className="form-label">Change Cover Image (Optional)</label>
                  <input
                    type="file"
                    className="form-input"
                    accept="image/*"
                    onChange={e => {
                      if (e.target.files && e.target.files[0]) {
                        const f = e.target.files[0];
                        setBookForm(prev => ({ ...prev, coverFile: f, coverPreview: URL.createObjectURL(f) }));
                      }
                    }}
                  />
                  <small className="text-muted" style={{ display: 'block', marginTop: '4px', fontSize: '12px' }}>
                    Select a new image file to update or replace the existing book cover.
                  </small>
                </div>
                {(bookForm.coverPreview || bookForm.existingCoverUrl) && (
                  <div className="cover-preview-box mt-3" style={{ maxWidth: '140px', borderRadius: 'var(--radius-md)', overflow: 'hidden', border: '1px solid var(--border-color)' }}>
                    <img
                      src={bookForm.coverPreview || getCoverUrl(bookForm.existingCoverUrl)}
                      alt="Cover Preview"
                      style={{ width: '100%', height: 'auto', display: 'block' }}
                    />
                  </div>
                )}
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-ghost" onClick={() => setActiveModal(null)}>Cancel</button>
                <button type="submit" className="btn btn-primary">Save Changes</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 3. Donate Book Modal (User / Member) */}
      {activeModal === 'createDonation' && (
        <div className="modal-backdrop open">
          <div className="modal-card">
            <div className="modal-header">
              <h3>Donate a Book to Padips Library</h3>
              <button className="btn-close" onClick={() => setActiveModal(null)}>&times;</button>
            </div>
            <form onSubmit={handleCreateDonationSubmit}>
              <div className="modal-body">
                <div className="form-group mb-4">
                  <label className="form-label">Book Title <span style={{ color: 'var(--accent-primary)' }}>*</span></label>
                  <input
                    type="text"
                    className="form-input"
                    required
                    value={donationForm.title}
                    onChange={e => setDonationForm({ ...donationForm, title: e.target.value })}
                    placeholder="e.g. Clean Code"
                  />
                </div>
                <div className="form-group mb-4">
                  <label className="form-label">Author Name <span style={{ color: 'var(--accent-primary)' }}>*</span></label>
                  <input
                    type="text"
                    className="form-input"
                    required
                    value={donationForm.author}
                    onChange={e => setDonationForm({ ...donationForm, author: e.target.value })}
                    placeholder="e.g. Robert C. Martin"
                  />
                </div>
                <div className="form-group mb-4">
                  <label className="form-label">ISBN Number <span style={{ color: 'var(--accent-primary)' }}>*</span></label>
                  <input
                    type="text"
                    className="form-input"
                    required
                    value={donationForm.isbn}
                    onChange={e => setDonationForm({ ...donationForm, isbn: e.target.value })}
                    placeholder="e.g. 9780132350884"
                  />
                </div>
                <div className="form-group mb-4">
                  <label className="form-label">Number of Copies Donated <span style={{ color: 'var(--accent-primary)' }}>*</span></label>
                  <input
                    type="number"
                    min="1"
                    className="form-input"
                    required
                    value={donationForm.donatedBookCount}
                    onChange={e => setDonationForm({ ...donationForm, donatedBookCount: e.target.value })}
                  />
                </div>
                <div className="form-group mb-2">
                  <label className="form-label">Book Cover Image (Optional)</label>
                  <input
                    type="file"
                    className="form-input"
                    accept="image/*"
                    onChange={e => {
                      if (e.target.files && e.target.files[0]) {
                        const f = e.target.files[0];
                        setDonationForm(prev => ({ ...prev, coverFile: f, coverPreview: URL.createObjectURL(f) }));
                      }
                    }}
                  />
                  <small className="text-muted" style={{ display: 'block', marginTop: '4px', fontSize: '12px' }}>
                    Attach a cover image photo or illustration for this book.
                  </small>
                </div>
                {donationForm.coverPreview && (
                  <div className="cover-preview-box mt-3" style={{ maxWidth: '140px', height: '140px', borderRadius: 'var(--radius-md)', overflow: 'hidden', border: '1px solid var(--border-color)', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--bg-app)' }}>
                    <img src={donationForm.coverPreview} alt="Donation Cover Preview" style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain', display: 'block' }} />
                  </div>
                )}
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-ghost" onClick={() => setActiveModal(null)} disabled={isSubmittingDonation}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={isSubmittingDonation}>
                  {isSubmittingDonation ? 'Submitting...' : 'Submit Donation'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 3.1 Edit Donation Modal */}
      {activeModal === 'editDonation' && (
        <div className="modal-backdrop open">
          <div className="modal-card">
            <div className="modal-header">
              <h3>Edit Book Donation</h3>
              <button className="btn-close" onClick={() => setActiveModal(null)} disabled={isSubmittingDonation}>&times;</button>
            </div>
            <form onSubmit={handleEditDonationSubmit}>
              <div className="modal-body">
                <div className="form-group mb-4">
                  <label className="form-label">Book Title <span style={{ color: 'var(--accent-primary)' }}>*</span></label>
                  <input
                    type="text"
                    className="form-input"
                    required
                    value={editDonationForm.title}
                    onChange={e => setEditDonationForm({ ...editDonationForm, title: e.target.value })}
                  />
                </div>
                <div className="form-group mb-4">
                  <label className="form-label">Author Name <span style={{ color: 'var(--accent-primary)' }}>*</span></label>
                  <input
                    type="text"
                    className="form-input"
                    required
                    value={editDonationForm.author}
                    onChange={e => setEditDonationForm({ ...editDonationForm, author: e.target.value })}
                  />
                </div>
                <div className="form-group mb-4">
                  <label className="form-label">ISBN Number <span style={{ color: 'var(--accent-primary)' }}>*</span></label>
                  <input
                    type="text"
                    className="form-input"
                    required
                    value={editDonationForm.isbn}
                    onChange={e => setEditDonationForm({ ...editDonationForm, isbn: e.target.value })}
                  />
                </div>
                <div className="form-group mb-4">
                  <label className="form-label">Number of Copies Donated <span style={{ color: 'var(--accent-primary)' }}>*</span></label>
                  <input
                    type="number"
                    min="1"
                    className="form-input"
                    required
                    value={editDonationForm.donatedBookCount}
                    onChange={e => setEditDonationForm({ ...editDonationForm, donatedBookCount: e.target.value })}
                  />
                </div>
                <div className="form-group mb-2">
                  <label className="form-label">Change Cover Image (Optional)</label>
                  <input
                    type="file"
                    className="form-input"
                    accept="image/*"
                    onChange={e => {
                      if (e.target.files && e.target.files[0]) {
                        const f = e.target.files[0];
                        setEditDonationForm(prev => ({ ...prev, coverFile: f, coverPreview: URL.createObjectURL(f) }));
                      }
                    }}
                  />
                  <small className="text-muted" style={{ display: 'block', marginTop: '4px', fontSize: '12px' }}>
                    Select a new image file to update the cover for this donation request.
                  </small>
                </div>
                {(editDonationForm.coverPreview || editDonationForm.existingCoverUrl) && (
                  <div className="cover-preview-box mt-3" style={{ maxWidth: '140px', height: '140px', borderRadius: 'var(--radius-md)', overflow: 'hidden', border: '1px solid var(--border-color)', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--bg-app)' }}>
                    <img
                      src={editDonationForm.coverPreview || getCoverUrl(editDonationForm.existingCoverUrl)}
                      alt="Donation Cover Preview"
                      style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain', display: 'block' }}
                    />
                  </div>
                )}
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-ghost" onClick={() => setActiveModal(null)} disabled={isSubmittingDonation}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={isSubmittingDonation}>
                  {isSubmittingDonation ? 'Saving...' : 'Save Changes'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 3.1 Reject Donation Modal (Admin) */}
      {rejectionModal.open && (
        <div className="modal-backdrop open">
          <div className="modal-card max-w-sm">
            <div className="modal-header">
              <h3>Reject Book Donation</h3>
              <button className="btn-close" onClick={() => setRejectionModal({ open: false, donationId: null, reason: '' })}>&times;</button>
            </div>
            <form onSubmit={handleRejectDonationSubmit}>
              <div className="modal-body">
                <p className="text-subtle mb-4">
                  Please provide an optional reason for rejecting Donation #{rejectionModal.donationId}.
                </p>
                <div className="form-group">
                  <label className="form-label">Rejection Reason</label>
                  <textarea
                    className="form-input"
                    rows="3"
                    value={rejectionModal.reason}
                    onChange={e => setRejectionModal({ ...rejectionModal, reason: e.target.value })}
                    placeholder="e.g. Duplicate title, damaged physical copies, or invalid ISBN"
                  />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-ghost" onClick={() => setRejectionModal({ open: false, donationId: null, reason: '' })}>Cancel</button>
                <button type="submit" className="btn btn-danger">Reject Donation</button>
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
                  <label className="form-label">Phone number</label>
                  <div className={`phone-input-control ${userForm.phoneError ? 'has-error' : ''}`}>
                    <select
                      className="phone-country-select"
                      value={userForm.countryCode}
                      onChange={e => setUserForm({ ...userForm, countryCode: e.target.value, phoneError: '' })}
                    >
                      {COUNTRY_CODES.map(c => (
                        <option key={`create-u-${c.code}-${c.dial}`} value={c.dial}>
                          {c.code} {c.dial}
                        </option>
                      ))}
                    </select>
                    <input
                      type="tel"
                      className="phone-number-field"
                      placeholder="555 555 5555"
                      value={userForm.phoneNumber}
                      onChange={e => {
                        const val = e.target.value.replace(/[^\d\s-]/g, '');
                        setUserForm(prev => {
                          const res = prev.phoneError ? validatePhoneNumber(prev.countryCode, val) : null;
                          return {
                            ...prev,
                            phoneNumber: val,
                            phoneError: (res && res.isValid) ? '' : prev.phoneError
                          };
                        });
                      }}
                      onBlur={() => {
                        if (userForm.phoneNumber.trim()) {
                          const res = validatePhoneNumber(userForm.countryCode, userForm.phoneNumber);
                          setUserForm(prev => ({ ...prev, phoneError: res.isValid ? '' : res.error }));
                        }
                      }}
                    />
                  </div>
                  {userForm.phoneError ? (
                    <small className="text-danger" style={{ display: 'block', marginTop: '6px', fontSize: '12px' }}>
                      {userForm.phoneError}
                    </small>
                  ) : (
                    <small className="text-muted" style={{ display: 'block', marginTop: '4px', fontSize: '12px' }}>
                      Optional: Country code and digits for notifications and Salesforce sync.
                    </small>
                  )}
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
                  <label className="form-label">Phone number</label>
                  <div className={`phone-input-control ${userForm.phoneError ? 'has-error' : ''}`}>
                    <select
                      className="phone-country-select"
                      value={userForm.countryCode}
                      onChange={e => setUserForm({ ...userForm, countryCode: e.target.value, phoneError: '' })}
                    >
                      {COUNTRY_CODES.map(c => (
                        <option key={`edit-u-${c.code}-${c.dial}`} value={c.dial}>
                          {c.code} {c.dial}
                        </option>
                      ))}
                    </select>
                    <input
                      type="tel"
                      className="phone-number-field"
                      placeholder="555 555 5555"
                      value={userForm.phoneNumber}
                      onChange={e => {
                        const val = e.target.value.replace(/[^\d\s-]/g, '');
                        setUserForm(prev => {
                          const res = prev.phoneError ? validatePhoneNumber(prev.countryCode, val) : null;
                          return {
                            ...prev,
                            phoneNumber: val,
                            phoneError: (res && res.isValid) ? '' : prev.phoneError
                          };
                        });
                      }}
                      onBlur={() => {
                        if (userForm.phoneNumber.trim()) {
                          const res = validatePhoneNumber(userForm.countryCode, userForm.phoneNumber);
                          setUserForm(prev => ({ ...prev, phoneError: res.isValid ? '' : res.error }));
                        }
                      }}
                    />
                  </div>
                  {userForm.phoneError ? (
                    <small className="text-danger" style={{ display: 'block', marginTop: '6px', fontSize: '12px' }}>
                      {userForm.phoneError}
                    </small>
                  ) : (
                    <small className="text-muted" style={{ display: 'block', marginTop: '4px', fontSize: '12px' }}>
                      Optional: Country code and digits for notifications and Salesforce sync.
                    </small>
                  )}
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
                    {allMembersForFines.map((m, idx) => (
                      <option key={m.id ?? m.uuid ?? m.email ?? `admin-b-m-${idx}`} value={m.id || ''}>{m.name || m.email}</option>
                    ))}
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Select Book Title</label>
                  <select className="form-select" required value={adminBorrowSelect.bookId} onChange={e => setAdminBorrowSelect({ ...adminBorrowSelect, bookId: e.target.value })}>
                    <option value="">-- Choose Book --</option>
                    {(modalBooks.length > 0 ? modalBooks : books).map((b, idx) => (
                      <option key={b.id ?? b.uuid ?? b.isbn ?? `admin-b-b-${idx}`} value={b.id ?? b.uuid ?? ''}>{b.title}</option>
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
                    {(modalBooks.length > 0 ? modalBooks : books).map((b, idx) => (
                      <option key={b.id ?? b.uuid ?? b.isbn ?? `user-b-${idx}`} value={b.id ?? b.uuid ?? ''}>{b.title} (by {b.author})</option>
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
