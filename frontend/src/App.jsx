import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useAuth0 } from '@auth0/auth0-react';
import { jwtDecode } from 'jwt-decode';
import {
  BookOpen, Users, LayoutDashboard, CreditCard, Award, Sun, Moon,
  Plus, Search, LogOut, X, ChevronDown, Check, Shield, FileText, Upload, RefreshCw,
  User as UserIcon, Edit2, Phone, Gift, Trash2, Bell, Lock, Clock, Landmark, ShieldCheck, Download
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
  const [theme, setTheme] = useState(localStorage.getItem('padips_theme') || 'dark');

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
  const [isUserSyncing, setIsUserSyncing] = useState(false);
  const [userRole, setUserRole] = useState('');
  const [showUserDropdown, setShowUserDropdown] = useState(false);
  const [authMode, setAuthMode] = useState('signin');
  const [signupForm, setSignupForm] = useState({
    username: '',
    aadhaarNumber: '',
    countryCode: '+91',
    phoneNumber: '',
    usernameError: '',
    aadhaarError: '',
    phoneError: '',
    generalError: '',
    isSubmitting: false
  });
  const userFetchedRef = useRef(false);

  // Navigation
  const [currentPage, setCurrentPage] = useState('dashboard');

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
  const [membersTab, setMembersTab] = useState('members'); // 'members' | 'reactivation'
  const [reactivationSearchQuery, setReactivationSearchQuery] = useState('');
  const [reactivationRequests, setReactivationRequests] = useState([]);
  const [reactivationTab, setReactivationTab] = useState('PENDING');
  const [requestingReactivation, setRequestingReactivation] = useState(false);
  const [refreshingStatus, setRefreshingStatus] = useState(false);

  // Borrows
  const [adminBorrows, setAdminBorrows] = useState([]);
  const [userBorrows, setUserBorrows] = useState([]);
  const [modalBooks, setModalBooks] = useState([]);

  // Fines
  const [fines, setFines] = useState([]);
  const [allMembersForFines, setAllMembersForFines] = useState([]);
  const [selectedFineMemberId, setSelectedFineMemberId] = useState('');
  const [fineTotalBalance, setFineTotalBalance] = useState(0);

  // Payments & Refunds
  const [paymentResult, setPaymentResult] = useState(null); // { type: 'success' | 'error' | 'warning' | 'info', title: string, message: string }
  const [isProcessingPayment, setIsProcessingPayment] = useState(false);

  // Bank Account Details State
  const [paymentProfile, setPaymentProfile] = useState(null);
  const [isLoadingPaymentProfile, setIsLoadingPaymentProfile] = useState(false);
  const [isSavingPaymentProfile, setIsSavingPaymentProfile] = useState(false);
  const [isEditingPaymentProfile, setIsEditingPaymentProfile] = useState(false);
  const [paymentProfileForm, setPaymentProfileForm] = useState({
    accountHolderName: '',
    bankName: '',
    accountNumber: '',
    ifscCode: '',
    formError: ''
  });

  // Payment History State
  const [paymentHistory, setPaymentHistory] = useState({
    totalPaid: 0,
    totalRefunded: 0,
    netPaid: 0,
    successfulTransactionCount: 0,
    content: [],
    currentPage: 0,
    pageSize: 10,
    totalElements: 0,
    totalPages: 1,
    isFirst: true,
    isLast: true,
    hasNext: false,
    hasPrevious: false
  });
  const [isLoadingPaymentHistory, setIsLoadingPaymentHistory] = useState(false);
  const [paymentHistoryPage, setPaymentHistoryPage] = useState(0);
  const [paymentHistoryType, setPaymentHistoryType] = useState('');
  const [paymentHistoryStatus, setPaymentHistoryStatus] = useState('');
  const [paymentHistorySearch, setPaymentHistorySearch] = useState('');
  const [expandedPaymentUuid, setExpandedPaymentUuid] = useState(null);
  const [downloadingInvoiceId, setDownloadingInvoiceId] = useState(null);

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
  const [confirmConfig, setConfirmConfig] = useState({ title: '', message: '', actionBtnText: 'Confirm', onConfirm: null });

  // Form models
  const [bookForm, setBookForm] = useState({ id: '', title: '', author: '', isbn: '', totalBookCount: 1, coverFile: null, coverPreview: '', existingCoverUrl: '' });
  const [userForm, setUserForm] = useState({ id: '', name: '', email: '', password: '', countryCode: '+1', phoneNumber: '', phoneError: '' });
  const [profileForm, setProfileForm] = useState({ name: '', email: '', countryCode: '+1', phoneNumber: '', phoneError: '' });
  const [promptPhoneForm, setPromptPhoneForm] = useState({ countryCode: '+1', phoneNumber: '', phoneError: '', isSubmitting: false });
  const [completeProfileForm, setCompleteProfileForm] = useState({
    username: '',
    aadhaarNumber: '',
    countryCode: '+91',
    phoneNumber: '',
    aadhaarError: '',
    usernameError: '',
    phoneError: '',
    isSubmitting: false
  });
  const [adminBorrowSelect, setAdminBorrowSelect] = useState({ memberId: '', bookId: '' });
  const [userBorrowBookId, setUserBorrowBookId] = useState('');

  // Toasts
  const [toasts, setToasts] = useState([]);

  // Reactivation request counts
  const pendingRequestsCount = reactivationRequests.filter(r =>
    r.reactivationRequestStatus === 'PENDING' || (r.reactivationRequested && r.reactivationRequestStatus !== 'APPROVED' && r.reactivationRequestStatus !== 'REJECTED')
  ).length;
  const approvedRequestsCount = reactivationRequests.filter(r => r.reactivationRequestStatus === 'APPROVED').length;
  const rejectedRequestsCount = reactivationRequests.filter(r => r.reactivationRequestStatus === 'REJECTED').length;

  const baseUrl = '/api';

  const loadRazorpayScript = () => {
    return new Promise((resolve) => {
      if (window.Razorpay) {
        resolve(true);
        return;
      }
      const existing = document.getElementById('razorpay-checkout-js');
      if (existing) {
        existing.onload = () => resolve(true);
        existing.onerror = () => resolve(false);
        return;
      }
      const script = document.createElement('script');
      script.id = 'razorpay-checkout-js';
      script.src = 'https://checkout.razorpay.com/v1/checkout.js';
      script.async = true;
      script.onload = () => resolve(true);
      script.onerror = () => resolve(false);
      document.body.appendChild(script);
    });
  };

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

    const resolvedUrl = endpoint.startsWith('http://') || endpoint.startsWith('https://')
      ? endpoint
      : endpoint.startsWith('/api')
        ? endpoint
        : `${baseUrl}${endpoint.startsWith('/') ? '' : '/'}${endpoint}`;

    try {
      const res = await fetch(resolvedUrl, { ...options, headers });
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
    localStorage.setItem('padips_theme', theme);
  }, [theme]);

  // Reusable profile refresh to keep reward points and user metadata up-to-date
  const refreshUserProfile = useCallback(async () => {
    if (!isAuthenticated) return;
    try {
      let reqHeaders = {};
      if (auth0User && auth0User.email) {
        reqHeaders['X-User-Email'] = auth0User.email.trim();
      }
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
  }, [isAuthenticated, auth0User, fetchApi]);

  const loadPaymentProfile = useCallback(async () => {
    if (!isAuthenticated) return null;
    setIsLoadingPaymentProfile(true);
    try {
      const res = await fetchApi('/user/payment-profile');
      if (res.ok && res.data) {
        setPaymentProfile(res.data);
        return res.data;
      } else {
        setPaymentProfile(null);
        return null;
      }
    } catch (err) {
      console.warn('Error loading payment profile:', err);
      setPaymentProfile(null);
      return null;
    } finally {
      setIsLoadingPaymentProfile(false);
    }
  }, [isAuthenticated, fetchApi]);

  const loadPaymentHistory = useCallback(async (page = 0, type = '', status = '', search = '') => {
    if (!isAuthenticated) return;
    setIsLoadingPaymentHistory(true);
    try {
      const params = new URLSearchParams();
      params.append('page', page);
      params.append('size', '10');
      if (type) params.append('paymentType', type);
      if (status) params.append('status', status);
      if (search && search.trim()) params.append('search', search.trim());

      const res = await fetchApi(`/user/me/payment-history?${params.toString()}`);
      if (res.ok && res.data) {
        setPaymentHistory(res.data);
      }
    } catch (err) {
      console.warn('Error loading payment history:', err);
    } finally {
      setIsLoadingPaymentHistory(false);
    }
  }, [isAuthenticated, fetchApi]);

  // Auth Initialization and User Sync
  useEffect(() => {
    if (!isAuthenticated) {
      userFetchedRef.current = false;
      setIsUserSyncing(false);
      return;
    }

    if (userFetchedRef.current) return;
    userFetchedRef.current = true;
    setIsUserSyncing(true);

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
        userObj['https://library-management.com/roles'],
        userObj['https://library-management.com/role'],
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
      try {
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
        const auth0Email = auth0User?.email || auth0User?.['https://library-management.com/email'];
        if (auth0Email) {
          reqHeaders['X-User-Email'] = auth0Email.trim();
        }
        const pendingUsername = sessionStorage.getItem('auth0_pending_username');
        if (pendingUsername && pendingUsername.trim()) {
          reqHeaders['X-User-Username'] = pendingUsername.trim();
          reqHeaders['X-User-Name'] = pendingUsername.trim();
        }
        const pendingAadhaar = sessionStorage.getItem('auth0_pending_aadhaar');
        if (pendingAadhaar && pendingAadhaar.trim()) {
          reqHeaders['X-User-Aadhaar'] = pendingAadhaar.trim();
        }
        const pendingPhone = sessionStorage.getItem('auth0_pending_phone');
        if (pendingPhone && pendingPhone.trim()) {
          reqHeaders['X-User-Phone'] = pendingPhone.trim();
        }

        let res = await fetchApi('/user/callback', { method: 'POST', headers: reqHeaders });
        if (!res.ok) {
          res = await fetchApi('/user/me', { headers: reqHeaders });
        }
        if (res.ok && res.data) {
          setCurrentUser(res.data);
          sessionStorage.removeItem('auth0_pending_username');
          sessionStorage.removeItem('auth0_pending_aadhaar');
          sessionStorage.removeItem('auth0_pending_phone');
          loadPaymentProfile();
          if (!res.data.phone && !sessionStorage.getItem('phone_prompt_dismissed')) {
            setPromptPhoneForm({ countryCode: '+1', phoneNumber: '', phoneError: '', isSubmitting: false });
            setActiveModal('missingPhonePrompt');
          }
        } else {
          showToast(res.data?.message || 'Failed to synchronize account profile with backend server.', 'error');
          setCurrentUser(null);
        }
      } catch (syncErr) {
        console.error('Error during user synchronization:', syncErr);
        showToast('Error synchronizing account profile.', 'error');
        setCurrentUser(null);
      } finally {
        setIsUserSyncing(false);
      }
    };

    syncUser();
  }, [isAuthenticated, auth0User, fetchApi, getAccessTokenSilently]);

  // Load Page Data on Change - Only fetch what is required for current page
  useEffect(() => {
    if (!isAuthenticated || !currentUser || currentUser.status === 'DEACTIVATED' || currentUser.status === 'PERMANENTLY_DEACTIVATED') return;
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
      loadPaymentProfile();
    } else if (currentPage === 'payments') {
      loadPaymentHistory(paymentHistoryPage, paymentHistoryType, paymentHistoryStatus, paymentHistorySearch);
    } else if (currentPage === 'donations') {
      loadDonations();
    }
  }, [currentPage, isAuthenticated, currentUser, userRole, booksPage, membersPage, memberSearchQuery, memberSortBy, memberSortDir, paymentHistoryPage, paymentHistoryType, paymentHistoryStatus, paymentHistorySearch]);

  // Load fines for deactivated user so pending fines can be reviewed and paid
  useEffect(() => {
    if (isAuthenticated && currentUser && (currentUser.status === 'DEACTIVATED' || currentUser.status === 'PERMANENTLY_DEACTIVATED')) {
      loadFines();
    }
  }, [isAuthenticated, currentUser?.status, currentUser?.hasPendingFines]);

  const handleSignOut = () => {
    userFetchedRef.current = false;
    setIsUserSyncing(false);
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
    if (url.startsWith('/api')) return url;
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
      const total = fList.reduce((acc, f) => acc + ((f.status !== 'PAID') ? (f.pendingFineAmount || f.amount || 0) : 0), 0);
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
      if (bookForm.coverFile && (createdBook?.uuid || createdBook?.id)) {
        const formData = new FormData();
        formData.append('file', bookForm.coverFile);
        await fetchApi(`/books/${createdBook.uuid || createdBook.id}/cover`, {
          method: 'POST',
          body: formData
        });
      }
      showToast('Book added to inventory successfully', 'success');
      setActiveModal(null);
      setBookForm({ id: '', uuid: '', title: '', author: '', isbn: '', totalBookCount: 1, coverFile: null, coverPreview: '', existingCoverUrl: '' });
      loadBooks(booksPage, bookSearchQuery);
    } else {
      showToast(res.data?.message || 'Failed to create book', 'error');
    }
  };

  const handleEditBookSubmit = async (e) => {
    e.preventDefault();
    const bookIdentifier = bookForm.uuid || bookForm.id;
    const res = await fetchApi(`/books/${bookIdentifier}`, {
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
        await fetchApi(`/books/${bookIdentifier}/cover`, {
          method: 'POST',
          body: formData
        });
      }
      showToast('Book updated successfully', 'success');
      setActiveModal(null);
      setBookForm({ id: '', uuid: '', title: '', author: '', isbn: '', totalBookCount: 1, coverFile: null, coverPreview: '', existingCoverUrl: '' });
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
      const list = res.data.content || res.data || [];
      setMembers(list);
      setMembersTotalPages(res.data.totalPages || 1);
    }
    const reqRes = await fetchApi('/user/reactivation-requests');
    if (reqRes.ok && reqRes.data) {
      setReactivationRequests(Array.isArray(reqRes.data) ? reqRes.data : []);
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

  const handleApproveReactivation = async (userId, name) => {
    setReactivationRequests(prev => prev.map(r => {
      if (r.id === userId || r.uuid === userId) {
        return { ...r, status: 'ACTIVE', reactivationRequestStatus: 'APPROVED', reactivationRequested: false };
      }
      return r;
    }));
    const res = await fetchApi(`/user/${userId}/approve-reactivation`, { method: 'POST' });
    if (res.ok) {
      showToast(`Member "${name}" reactivated successfully`, 'success');
      await loadMembers(membersPage, memberSearchQuery);
      const reqRes = await fetchApi('/user/reactivation-requests');
      if (reqRes.ok && Array.isArray(reqRes.data)) {
        setReactivationRequests(reqRes.data);
      }
    } else {
      showToast(res.data?.message || 'Failed to reactivate member', 'error');
      await loadMembers(membersPage, memberSearchQuery);
      const reqRes = await fetchApi('/user/reactivation-requests');
      if (reqRes.ok && Array.isArray(reqRes.data)) {
        setReactivationRequests(reqRes.data);
      }
    }
  };

  const handleRejectReactivation = async (userId, name) => {
    const res = await fetchApi(`/user/${userId}/reject-reactivation`, { method: 'POST' });
    if (res.ok) {
      const updatedStatus = res.data?.status || 'DEACTIVATED';
      setReactivationRequests(prev => prev.map(r => {
        if (r.id === userId || r.uuid === userId) {
          return { ...r, status: updatedStatus, reactivationRequestStatus: 'REJECTED', reactivationRequested: false };
        }
        return r;
      }));
      showToast(`Reactivation request for "${name}" rejected`, 'info');
      await loadMembers(membersPage, memberSearchQuery);
      const reqRes = await fetchApi('/user/reactivation-requests');
      if (reqRes.ok && Array.isArray(reqRes.data)) {
        setReactivationRequests(reqRes.data);
      }
    } else {
      showToast(res.data?.message || 'Failed to reject reactivation request', 'error');
      await loadMembers(membersPage, memberSearchQuery);
      const reqRes = await fetchApi('/user/reactivation-requests');
      if (reqRes.ok && Array.isArray(reqRes.data)) {
        setReactivationRequests(reqRes.data);
      }
    }
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
        const total = fList.reduce((acc, f) => acc + ((f.status !== 'PAID') ? (f.pendingFineAmount || f.amount || 0) : 0), 0);
        setFineTotalBalance(total);
      }
    } else {
      const res = await fetchApi('/fines/me');
      if (res.ok && res.data) {
        const fList = Array.isArray(res.data) ? res.data : [];
        setFines(fList);
        const total = fList.reduce((acc, f) => acc + ((f.status !== 'PAID') ? (f.pendingFineAmount || f.amount || 0) : 0), 0);
        setFineTotalBalance(total);
      }
    }
  };

  const handlePayFine = async (fineId) => {
    if (!fineId) return;
    setIsProcessingPayment(true);
    try {
      const scriptLoaded = await loadRazorpayScript();
      if (!scriptLoaded) {
        showToast('Could not load Razorpay payment gateway. Please check your network connection.', 'error');
        setIsProcessingPayment(false);
        return;
      }

      const res = await fetchApi(`/payments/fine/${fineId}/order`, { method: 'POST' });
      if (!res.ok || !res.data) {
        showToast(res.data?.message || 'Failed to initialize fine payment order', 'error');
        setIsProcessingPayment(false);
        return;
      }

      const orderData = res.data;
      const options = {
        key: orderData.keyId,
        amount: orderData.amount,
        currency: 'INR',
        name: 'Padips',
        description: orderData.description || `Overdue Book Fine Payment #${fineId}`,
        order_id: orderData.orderId,
        prefill: {
          name: paymentProfile?.accountHolderName || currentUser?.name || '',
          email: currentUser?.email || '',
          contact: currentUser?.phone || ''
        },
        theme: {
          color: '#6366f1'
        },
        handler: async function (response) {
          try {
            const verifyRes = await fetchApi('/payments/verify', {
              method: 'POST',
              body: JSON.stringify({
                razorpayOrderId: response.razorpay_order_id,
                razorpayPaymentId: response.razorpay_payment_id,
                razorpaySignature: response.razorpay_signature
              })
            });

            if (verifyRes.ok) {
              setPaymentResult({
                type: 'success',
                title: 'Fine Settled Successfully!',
                message: `Fine payment of ₹${(orderData.amount / 100).toFixed(2)} confirmed (Payment ID: ${response.razorpay_payment_id}). Outstanding dues updated.`
              });
              showToast('Fine payment settled successfully', 'success');
              await loadFines();
              await loadUserFinesTotal();
              const meRes = await fetchApi('/user/me');
              if (meRes.ok && meRes.data) {
                setCurrentUser(meRes.data);
              }
            } else {
              setPaymentResult({
                type: 'error',
                title: 'Payment Verification Failed',
                message: verifyRes.data?.message || 'Payment received by gateway but backend verification failed.'
              });
              showToast('Payment verification failed', 'error');
            }
          } catch (verErr) {
            console.error('Error verifying fine payment:', verErr);
          } finally {
            setIsProcessingPayment(false);
          }
        },
        modal: {
          ondismiss: function () {
            setIsProcessingPayment(false);
            setPaymentResult({
              type: 'warning',
              title: 'Fine Payment Cancelled',
              message: 'Payment window was closed. Your fine remains unpaid and can be settled anytime.'
            });
          }
        }
      };

      const rzp = new window.Razorpay(options);
      rzp.on('payment.failed', async function (response) {
        setIsProcessingPayment(false);
        const err = response?.error || {};
        const meta = err.metadata || {};
        const failureMsg = err.description || err.reason || 'Your payment was declined. The fine remains unpaid.';
        setPaymentResult({
          type: 'error',
          title: 'Fine Payment Failed',
          message: failureMsg
        });
        showToast(failureMsg, 'error');

        try {
          await fetchApi('/payments/verify', {
            method: 'POST',
            body: JSON.stringify({
              razorpayOrderId: meta.order_id || orderData.orderId,
              razorpayPaymentId: meta.payment_id || null,
              razorpaySignature: null
            })
          });
          loadPaymentHistory(paymentHistoryPage, paymentHistoryType, paymentHistoryStatus, paymentHistorySearch);
          loadFines();
          loadUserFinesTotal();
        } catch (failErr) {
          console.error('Error reporting fine payment failure:', failErr);
        }
      });
      rzp.open();
    } catch (err) {
      console.error('Fine payment error:', err);
      setIsProcessingPayment(false);
      showToast('Failed to start fine payment checkout.', 'error');
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
          setAgreementText(typeof resTerms.data === 'string' ? resTerms.data : resTerms.data.terms || resTerms.data.agreementHtml || 'Padips Library Membership Agreement...');
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
      showToast('Agreement signed successfully! Please complete your ₹500 security deposit payment to activate your membership.', 'info');
      setSigFile(null);
      setSigPreviewUrl('');
      await loadMembership(true);
    } else {
      showToast(res.data?.message || 'Failed to sign agreement', 'error');
    }
  };

  const handlePayMembership = async () => {
    setIsProcessingPayment(true);
    try {
      const scriptLoaded = await loadRazorpayScript();
      if (!scriptLoaded) {
        showToast('Could not load Razorpay payment gateway. Please check your network connection.', 'error');
        setIsProcessingPayment(false);
        return;
      }

      const res = await fetchApi('/payments/membership/order', { method: 'POST' });
      if (!res.ok || !res.data) {
        showToast(res.data?.message || 'Failed to initialize membership payment order', 'error');
        setIsProcessingPayment(false);
        return;
      }

      const orderData = res.data;
      const options = {
        key: orderData.keyId,
        amount: orderData.amount,
        currency: 'INR',
        name: 'Padips',
        description: orderData.description || '1-Year Library Membership Security Deposit',
        order_id: orderData.orderId,
        prefill: {
          name: paymentProfile?.accountHolderName || currentUser?.name || '',
          email: currentUser?.email || '',
          contact: currentUser?.phone || ''
        },
        theme: {
          color: '#6366f1'
        },
        handler: async function (response) {
          try {
            const verifyRes = await fetchApi('/payments/verify', {
              method: 'POST',
              body: JSON.stringify({
                razorpayOrderId: response.razorpay_order_id,
                razorpayPaymentId: response.razorpay_payment_id,
                razorpaySignature: response.razorpay_signature
              })
            });

            if (verifyRes.ok) {
              setPaymentResult({
                type: 'success',
                title: 'Membership Activated for 1 Year!',
                message: `Payment of ₹${(orderData.amount / 100).toFixed(2)} confirmed (Payment ID: ${response.razorpay_payment_id}). Your digital library membership is now ACTIVE for 1 year!`
              });
              showToast('Membership activated successfully!', 'success');
              await loadMembership(false);
            } else {
              setPaymentResult({
                type: 'error',
                title: 'Payment Verification Failed',
                message: verifyRes.data?.message || 'Payment received by gateway but backend verification failed.'
              });
              showToast('Payment verification failed', 'error');
            }
          } catch (verErr) {
            console.error('Error verifying membership payment:', verErr);
          } finally {
            setIsProcessingPayment(false);
          }
        },
        modal: {
          ondismiss: function () {
            setIsProcessingPayment(false);
            setPaymentResult({
              type: 'warning',
              title: 'Payment Incomplete',
              message: 'Checkout closed without payment. Your signed agreement is saved — you can click "Pay ₹500 via Razorpay" below anytime to activate.'
            });
          }
        }
      };

      const rzp = new window.Razorpay(options);
      rzp.on('payment.failed', async function (response) {
        setIsProcessingPayment(false);
        const err = response?.error || {};
        const meta = err.metadata || {};
        const failureMsg = err.description || err.reason || 'Payment was declined by gateway. You can retry paying without re-signing the agreement.';
        setPaymentResult({
          type: 'error',
          title: 'Payment Declined',
          message: failureMsg
        });
        showToast('Payment failed. You can retry anytime.', 'error');

        try {
          await fetchApi('/payments/verify', {
            method: 'POST',
            body: JSON.stringify({
              razorpayOrderId: meta.order_id || orderData.orderId,
              razorpayPaymentId: meta.payment_id || null,
              razorpaySignature: null
            })
          });
          loadPaymentHistory(paymentHistoryPage, paymentHistoryType, paymentHistoryStatus, paymentHistorySearch);
          loadMembership(false);
        } catch (failErr) {
          console.error('Error reporting membership payment failure:', failErr);
        }
      });
      rzp.open();
    } catch (err) {
      console.error('Payment checkout error:', err);
      setIsProcessingPayment(false);
      showToast('Failed to start payment checkout.', 'error');
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

  const handleDownloadInvoice = async (item) => {
    const paymentId = item.uuid || item.id;
    if (!paymentId) {
      showToast('Payment identifier not found', 'error');
      return;
    }
    setDownloadingInvoiceId(paymentId);
    try {
      const res = await fetchApi(`/payments/${paymentId}/invoice`);
      if (res.ok && res.data instanceof Blob) {
        const blobUrl = URL.createObjectURL(res.data);
        const link = document.createElement('a');
        link.href = blobUrl;
        const rawName = currentUser?.name || currentUser?.email || 'User';
        const namePart = rawName.includes('@') ? rawName.split('@')[0] : rawName;
        const cleanUsername = namePart.replace(/[^a-zA-Z0-9_-]/g, '_');
        const filename = item.status === 'SUCCESS' ? `Padips-Receipt-${cleanUsername}.pdf` : `Padips-Statement-${cleanUsername}.pdf`;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(blobUrl);
        showToast('Payment document downloaded successfully', 'success');
      } else {
        showToast(res.data?.message || 'Failed to download payment document', 'error');
      }
    } catch (err) {
      console.error('Failed to download payment document:', err);
      showToast('Error downloading payment document', 'error');
    } finally {
      setDownloadingInvoiceId(null);
    }
  };

  const handleCancelMembership = () => {
    setConfirmConfig({
      title: 'Cancel Library Membership & Refund Deposit',
      message: 'Are you sure you want to cancel your membership? Your ₹500 security deposit will be refunded to your original payment method minus any outstanding unpaid fines. Any unreturned books will continue to accrue overdue fines.',
      actionBtnText: 'Confirm Cancellation & Refund',
      onConfirm: async () => {
        const res = await fetchApi('/payments/membership/cancel-with-refund', { method: 'POST' });
        if (res.ok && res.data) {
          const ref = res.data;
          setPaymentResult({
            type: 'info',
            title: 'Membership Cancelled',
            message: `${ref.message} (Security Deposit: ₹${(ref.originalDepositAmount / 100).toFixed(2)}, Unpaid Fines Deducted: ₹${(ref.unpaidFinesDeducted / 100).toFixed(2)}, Net Refund: ₹${(ref.refundAmount / 100).toFixed(2)}${ref.razorpayRefundId ? ` | Refund ID: ${ref.razorpayRefundId}` : ''})`
          });
          showToast('Membership cancelled and refund processed', 'success');
          loadMembership(false);
          loadFines();
          loadUserFinesTotal();
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

  const handleCompleteProfileSubmit = async (e) => {
    if (e) e.preventDefault();

    let hasErr = false;
    let aadhaarErr = '';
    let phoneErr = '';

    const cleanUsername = (completeProfileForm.username || '').trim() ||
      (currentUser && currentUser.name && currentUser.name.trim() !== '' && currentUser.name.trim() !== 'User' ? currentUser.name.trim() : '') ||
      (auth0User?.name || auth0User?.nickname || (auth0User?.email ? auth0User.email.split('@')[0] : 'User'));

    const cleanAadhaarDigits = (completeProfileForm.aadhaarNumber || '').replace(/[\s-]+/g, '');
    if (!cleanAadhaarDigits) {
      aadhaarErr = 'Aadhaar number is mandatory';
      hasErr = true;
    } else if (!/^\d{12}$/.test(cleanAadhaarDigits)) {
      aadhaarErr = 'Aadhaar number must consist of exactly 12 numeric digits';
      hasErr = true;
    }

    let formattedPhone = null;
    if (completeProfileForm.phoneNumber && completeProfileForm.phoneNumber.trim()) {
      const valPhone = validatePhoneNumber(completeProfileForm.countryCode, completeProfileForm.phoneNumber);
      if (!valPhone.isValid) {
        phoneErr = valPhone.error;
        hasErr = true;
      } else {
        formattedPhone = `${completeProfileForm.countryCode}${valPhone.cleanDigits}`;
      }
    }

    setCompleteProfileForm(prev => ({
      ...prev,
      aadhaarError: aadhaarErr,
      phoneError: phoneErr
    }));

    if (hasErr) return;

    setCompleteProfileForm(prev => ({ ...prev, isSubmitting: true }));
    try {
      const payload = {
        username: cleanUsername,
        aadhaarNumber: cleanAadhaarDigits,
        phone: formattedPhone
      };

      const res = await fetchApi('/user/complete-profile', {
        method: 'POST',
        body: JSON.stringify(payload)
      });

      if (res.ok && res.data) {
        setCurrentUser(res.data);
        showToast('Profile completed successfully! Welcome to Padips.', 'success');
        setCompleteProfileForm({
          username: '',
          aadhaarNumber: '',
          countryCode: '+91',
          phoneNumber: '',
          aadhaarError: '',
          usernameError: '',
          phoneError: '',
          isSubmitting: false
        });
      } else {
        const errMsg = res.data?.message || 'Failed to complete profile';
        if (errMsg.toLowerCase().includes('aadhaar')) {
          setCompleteProfileForm(prev => ({ ...prev, aadhaarError: errMsg }));
        } else if (errMsg.toLowerCase().includes('username')) {
          setCompleteProfileForm(prev => ({ ...prev, usernameError: errMsg }));
        } else if (errMsg.toLowerCase().includes('phone') || errMsg.toLowerCase().includes('mobile')) {
          setCompleteProfileForm(prev => ({ ...prev, phoneError: errMsg }));
        }
        showToast(errMsg, 'error');
      }
    } catch (err) {
      console.error('Error submitting complete profile:', err);
      showToast('An error occurred while saving your profile', 'error');
    } finally {
      setCompleteProfileForm(prev => ({ ...prev, isSubmitting: false }));
    }
  };

  // Bank Account Profile Functions & Helpers
  const formatIfsc = (val) => {
    return (val || '').replace(/[^a-zA-Z0-9]/g, '').slice(0, 11).toUpperCase();
  };

  const openPaymentProfileModal = (forceEdit = false) => {
    if (paymentProfile && !forceEdit) {
      setIsEditingPaymentProfile(false);
    } else {
      setIsEditingPaymentProfile(true);
      setPaymentProfileForm({
        accountHolderName: paymentProfile?.accountHolderName || currentUser?.name || '',
        bankName: paymentProfile?.bankName || '',
        accountNumber: '',
        ifscCode: paymentProfile?.ifscCode || '',
        formError: ''
      });
    }
    setActiveModal('paymentProfile');
  };

  const handleSavePaymentProfile = async (e) => {
    e.preventDefault();
    const { accountHolderName, bankName, accountNumber, ifscCode } = paymentProfileForm;

    if (!accountHolderName.trim()) {
      setPaymentProfileForm(prev => ({ ...prev, formError: 'Account holder name is required' }));
      return;
    }
    if (!bankName.trim()) {
      setPaymentProfileForm(prev => ({ ...prev, formError: 'Bank name is required' }));
      return;
    }
    if (!accountNumber.trim()) {
      setPaymentProfileForm(prev => ({ ...prev, formError: 'Account number is required' }));
      return;
    }
    const cleanIfsc = ifscCode.trim().toUpperCase();
    const ifscRegex = /^[A-Z]{4}0[A-Z0-9]{6}$/;
    if (!ifscRegex.test(cleanIfsc)) {
      setPaymentProfileForm(prev => ({ ...prev, formError: 'Invalid IFSC code format (e.g. HDFC0001234)' }));
      return;
    }

    setIsSavingPaymentProfile(true);
    setPaymentProfileForm(prev => ({ ...prev, formError: '' }));

    const payload = {
      accountHolderName: accountHolderName.trim(),
      bankName: bankName.trim(),
      accountNumber: accountNumber.trim(),
      ifscCode: cleanIfsc
    };

    const res = await fetchApi('/user/payment-profile', {
      method: 'PUT',
      body: JSON.stringify(payload)
    });

    setIsSavingPaymentProfile(false);

    if (res.ok && res.data) {
      setPaymentProfile(res.data);
      setIsEditingPaymentProfile(false);
      showToast('Bank account details saved successfully', 'success');
    } else {
      const errMsg = res.data?.message || 'Failed to save bank account details';
      setPaymentProfileForm(prev => ({ ...prev, formError: errMsg }));
      showToast(errMsg, 'error');
    }
  };

  const handleDeletePaymentProfile = () => {
    setConfirmConfig({
      title: 'Delete Saved Bank Account',
      message: 'Are you sure you want to delete your saved bank account details? You will need to re-enter them for refunds or security deposit returns.',
      actionBtnText: 'Delete Account',
      onConfirm: async () => {
        const res = await fetchApi('/user/payment-profile', { method: 'DELETE' });
        if (res.ok) {
          setPaymentProfile(null);
          setIsEditingPaymentProfile(false);
          setActiveModal(null);
          showToast('Bank account details removed', 'info');
        } else {
          showToast(res.data?.message || 'Failed to delete bank details', 'error');
        }
      }
    });
    setActiveModal('confirm');
  };


  // Render loading screen while Auth0 verifies session or profile is synchronizing
  if (isLoading || (isAuthenticated && (isUserSyncing || !currentUser))) {
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
          <h3 style={{ fontSize: '18px', fontWeight: 600, marginBottom: '8px' }}>
            {isLoading ? 'Authenticating session...' : 'Loading profile...'}
          </h3>
          <p style={{ color: '#888', fontSize: '14px' }}>
            {isLoading ? 'Connecting to Auth0 Identity Provider' : 'Verifying account status and permissions'}
          </p>
        </div>
      </div>
    );
  }

  // Render Auth0 Login / Signup Screen if not authenticated
  if (!isAuthenticated) {
    const handleSignupSubmit = (e) => {
      if (e) e.preventDefault();

      let hasErr = false;
      let userErr = '';
      let aadhErr = '';
      let phErr = '';

      const cleanUser = (signupForm.username || '').trim();
      if (!cleanUser) {
        userErr = 'Username is mandatory';
        hasErr = true;
      }

      const cleanAadhaar = (signupForm.aadhaarNumber || '').replace(/[\s-]+/g, '');
      if (!cleanAadhaar) {
        aadhErr = 'Aadhaar number is mandatory';
        hasErr = true;
      } else if (!/^\d{12}$/.test(cleanAadhaar)) {
        aadhErr = 'Aadhaar number must consist of exactly 12 numeric digits';
        hasErr = true;
      }

      let formattedPhone = '';
      if (signupForm.phoneNumber && signupForm.phoneNumber.trim()) {
        const valPhone = validatePhoneNumber(signupForm.countryCode, signupForm.phoneNumber);
        if (!valPhone.isValid) {
          phErr = valPhone.error;
          hasErr = true;
        } else {
          formattedPhone = `${signupForm.countryCode}${valPhone.cleanDigits}`;
        }
      }

      setSignupForm(prev => ({
        ...prev,
        usernameError: userErr,
        aadhaarError: aadhErr,
        phoneError: phErr,
        generalError: ''
      }));

      if (hasErr) return;

      // Persist signup credentials in sessionStorage across Auth0 Universal Login redirect
      sessionStorage.setItem('auth0_pending_username', cleanUser);
      sessionStorage.setItem('auth0_pending_aadhaar', cleanAadhaar);
      if (formattedPhone) {
        sessionStorage.setItem('auth0_pending_phone', formattedPhone);
      } else {
        sessionStorage.removeItem('auth0_pending_phone');
      }

      loginWithRedirect({
        authorizationParams: {
          screen_hint: 'signup'
        }
      });
    };

    const handleDirectLogin = () => {
      loginWithRedirect();
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
              onClick={() => setAuthMode('signin')}
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
              onClick={() => setAuthMode('signup')}
            >
              Sign Up
            </button>
          </div>

          <div className="auth-header">
            <h2>{authMode === 'signin' ? 'Sign in to portal' : 'Create your account'}</h2>
          </div>

          {auth0Error && (
            <div className="alert alert-danger mb-4">
              {auth0Error.message || 'Authentication error occurred'}
            </div>
          )}

          {authMode === 'signin' ? (
            <div>
              <button
                type="button"
                className="btn btn-primary btn-block btn-lg mb-3"
                onClick={handleDirectLogin}
              >
                <span className="btn-text">Sign In</span>
              </button>
              <div style={{ textAlign: 'center', marginTop: '16px' }}>
                <span style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
                  Don't have an account?{' '}
                  <button
                    type="button"
                    style={{ background: 'none', border: 'none', color: 'var(--accent-primary)', cursor: 'pointer', textDecoration: 'underline', padding: 0 }}
                    onClick={() => setAuthMode('signup')}
                  >
                    Register
                  </button>
                </span>
              </div>
            </div>
          ) : (
            <form onSubmit={handleSignupSubmit}>
              {signupForm.generalError && (
                <div className="alert alert-danger mb-3" style={{ fontSize: '13px', padding: '10px 14px' }}>
                  {signupForm.generalError}
                </div>
              )}

              <div className="form-group mb-3" style={{ textAlign: 'left' }}>
                <label className="form-label" style={{ display: 'block', marginBottom: '6px', fontSize: '13px', fontWeight: 600 }}>
                  Username <span style={{ color: 'var(--accent-primary)' }}>*</span>
                </label>
                <input
                  type="text"
                  className={`form-input ${signupForm.usernameError ? 'border-danger' : ''}`}
                  style={{
                    width: '100%',
                    padding: '10px 14px',
                    borderRadius: 'var(--radius-md)',
                    border: signupForm.usernameError ? '1px solid #ef4444' : '1px solid var(--border-medium)',
                    background: 'var(--bg-input)',
                    color: 'var(--text-main)',
                    fontSize: '14px'
                  }}
                  placeholder="e.g. johndoe"
                  value={signupForm.username}
                  onChange={(e) => setSignupForm({ ...signupForm, username: e.target.value, usernameError: '', generalError: '' })}
                  autoFocus
                  required
                />
                {signupForm.usernameError && (
                  <small className="text-danger" style={{ display: 'block', marginTop: '4px', fontSize: '12px' }}>
                    {signupForm.usernameError}
                  </small>
                )}
              </div>

              <div className="form-group mb-3" style={{ textAlign: 'left' }}>
                <label className="form-label" style={{ display: 'block', marginBottom: '6px', fontSize: '13px', fontWeight: 600 }}>
                  Aadhaar Number <span style={{ color: 'var(--accent-primary)' }}>*</span>
                </label>
                <input
                  type="text"
                  maxLength={14}
                  className={`form-input ${signupForm.aadhaarError ? 'border-danger' : ''}`}
                  style={{
                    width: '100%',
                    padding: '10px 14px',
                    borderRadius: 'var(--radius-md)',
                    border: signupForm.aadhaarError ? '1px solid #ef4444' : '1px solid var(--border-medium)',
                    background: 'var(--bg-input)',
                    color: 'var(--text-main)',
                    fontSize: '14px',
                    fontFamily: 'monospace',
                    letterSpacing: '1px'
                  }}
                  placeholder="1234 5678 9012"
                  value={signupForm.aadhaarNumber}
                  onChange={(e) => {
                    const raw = e.target.value.replace(/[^\d]/g, '').slice(0, 12);
                    const formatted = raw.replace(/(\d{4})(?=\d)/g, '$1 ');
                    setSignupForm({ ...signupForm, aadhaarNumber: formatted, aadhaarError: '', generalError: '' });
                  }}
                  required
                />
                {signupForm.aadhaarError && (
                  <small className="text-danger" style={{ display: 'block', marginTop: '4px', fontSize: '12px' }}>
                    {signupForm.aadhaarError}
                  </small>
                )}
              </div>

              <div className="form-group mb-4" style={{ textAlign: 'left' }}>
                <label className="form-label" style={{ display: 'block', marginBottom: '6px', fontSize: '13px', fontWeight: 600 }}>
                  Mobile Number
                </label>
                <div className={`phone-input-control ${signupForm.phoneError ? 'has-error' : ''}`}>
                  <select
                    className="phone-country-select"
                    value={signupForm.countryCode}
                    onChange={e => setSignupForm({ ...signupForm, countryCode: e.target.value, phoneError: '' })}
                  >
                    {COUNTRY_CODES.map(c => (
                      <option key={`signup-${c.code}-${c.dial}`} value={c.dial}>
                        {c.code} {c.dial}
                      </option>
                    ))}
                  </select>
                  <input
                    type="tel"
                    className="phone-number-field"
                    placeholder="98765 43210"
                    value={signupForm.phoneNumber}
                    onChange={e => {
                      const val = e.target.value.replace(/[^\d\s-]/g, '');
                      setSignupForm(prev => ({ ...prev, phoneNumber: val, phoneError: '', generalError: '' }));
                    }}
                  />
                </div>
                {signupForm.phoneError && (
                  <small className="text-danger" style={{ display: 'block', marginTop: '4px', fontSize: '12px' }}>
                    {signupForm.phoneError}
                  </small>
                )}
              </div>

              <button
                type="submit"
                className="btn btn-primary btn-block btn-lg mb-3"
                disabled={signupForm.isSubmitting}
              >
                <span className="btn-text">Sign Up with Auth0</span>
              </button>

              <div style={{ textAlign: 'center', marginTop: '16px' }}>
                <span style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
                  Already have an account?{' '}
                  <button
                    type="button"
                    style={{ background: 'none', border: 'none', color: 'var(--accent-primary)', cursor: 'pointer', textDecoration: 'underline', padding: 0 }}
                    onClick={() => setAuthMode('signin')}
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

  // Render error screen if authenticated but profile synchronization failed
  if (isAuthenticated && !currentUser) {
    return (
      <div className="auth-screen">
        <div className="auth-card" style={{ textAlign: 'center', padding: '48px 32px' }}>
          <div className="auth-brand" style={{ justifyContent: 'center', marginBottom: '24px' }}>
            <div className="brand-icon" style={{ background: 'rgba(239, 68, 68, 0.15)', color: '#ef4444', margin: '0 auto 16px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Lock size={28} />
            </div>
            <h1 className="brand-title">Padips</h1>
          </div>
          <h3 style={{ fontSize: '18px', fontWeight: 600, marginBottom: '12px', color: '#ef4444' }}>Profile Synchronization Failed</h3>
          <p style={{ color: 'var(--text-muted)', fontSize: '14px', marginBottom: '24px' }}>
            Unable to load your account profile from the server. Please sign in again.
          </p>
          <button type="button" className="btn btn-primary" onClick={handleSignOut}>
            Sign Out & Try Again
          </button>
        </div>
      </div>
    );
  }

  // Render Deactivated or Permanently Deactivated Account Screen
  if (currentUser && (currentUser.status === 'DEACTIVATED' || currentUser.status === 'PERMANENTLY_DEACTIVATED')) {
    const isPending = currentUser.reactivationRequestStatus === 'PENDING' || currentUser.reactivationRequested;
    const isPermanentlyDeactivated = currentUser.status === 'PERMANENTLY_DEACTIVATED' && !isPending;

    const unpaidFines = fines.filter(f => f.status === 'PENDING' || f.status === 'UNPAID' || f.status !== 'PAID');
    const hasPendingFines = Boolean(
      currentUser.hasPendingFines ||
      (currentUser.pendingFineAmount && currentUser.pendingFineAmount > 0) ||
      unpaidFines.length > 0
    );
    const totalPendingFine = (currentUser.pendingFineAmount && currentUser.pendingFineAmount > 0)
      ? currentUser.pendingFineAmount
      : unpaidFines.reduce((acc, f) => acc + (f.pendingFineAmount || f.amount || 0), 0) || fineTotalBalance || 0;

    const handleReactivateRequest = async () => {
      setRequestingReactivation(true);
      const res = await fetchApi('/user/me/request-reactivation', { method: 'POST' });
      setRequestingReactivation(false);
      if (res.ok && res.data) {
        setCurrentUser(res.data);
        showToast('Account reactivation requested successfully. Awaiting admin approval.', 'success');
      } else {
        const errMsg = res.data?.message || 'Failed to request reactivation';
        showToast(errMsg, 'error');
        if (errMsg.toLowerCase().includes('permanently deactivated') || errMsg.toLowerCase().includes('expired')) {
          setCurrentUser(prev => ({ ...prev, status: 'PERMANENTLY_DEACTIVATED' }));
        }
      }
    };

    const handleRefreshStatus = async () => {
      setRefreshingStatus(true);
      const res = await fetchApi('/user/me');
      await loadFines();
      setRefreshingStatus(false);
      if (res.ok && res.data) {
        setCurrentUser(res.data);
        if (res.data.status === 'ACTIVE') {
          showToast('Your account has been reactivated! Welcome back.', 'success');
        } else {
          showToast('Account status updated.', 'info');
        }
      } else {
        showToast('Failed to refresh status.', 'error');
      }
    };

    return (
      <div className="auth-screen">
        <div className="auth-card" style={{ maxWidth: '540px', textAlign: 'center', padding: '36px' }}>
          <div className="auth-brand" style={{ marginBottom: '24px' }}>
            <div className="brand-icon" style={{
              background: isPermanentlyDeactivated
                ? 'rgba(239, 68, 68, 0.2)'
                : isPending
                  ? 'rgba(245, 158, 11, 0.2)'
                  : 'rgba(239, 68, 68, 0.15)',
              color: isPermanentlyDeactivated ? '#ef4444' : isPending ? '#f59e0b' : '#ef4444',
              margin: '0 auto 16px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}>
              {isPermanentlyDeactivated ? <Lock size={32} /> : isPending ? <Clock size={32} /> : <Shield size={32} />}
            </div>
            <h1 className="brand-title" style={{
              fontSize: '24px',
              fontWeight: 700,
              color: isPermanentlyDeactivated ? '#ef4444' : isPending ? '#f59e0b' : undefined
            }}>
              {isPermanentlyDeactivated
                ? 'Account Permanently Deactivated'
                : isPending
                  ? 'Reactivation Request Pending Review'
                  : 'Account Deactivated'}
            </h1>
            <p className="brand-subtitle" style={{ fontSize: '14px', color: 'var(--text-muted)' }}>
              Logged in as <strong>{currentUser.name || currentUser.email}</strong>
            </p>
          </div>

          {paymentResult && (
            <div className={`payment-result-banner banner-${paymentResult.type || 'info'}`} style={{ textAlign: 'left', marginBottom: '20px' }}>
              <div style={{ display: 'flex', gap: '10px', alignItems: 'flex-start' }}>
                <span style={{ fontSize: '20px', lineHeight: 1 }}>
                  {paymentResult.type === 'success' ? '✅' : paymentResult.type === 'warning' ? '⚠️' : 'ℹ️'}
                </span>
                <div>
                  <strong style={{ fontSize: '14px', display: 'block', marginBottom: '2px' }}>{paymentResult.title}</strong>
                  <p style={{ margin: 0, fontSize: '13px', lineHeight: 1.4 }}>{paymentResult.message}</p>
                </div>
              </div>
              <button
                type="button"
                className="btn-link"
                style={{ background: 'transparent', border: 'none', cursor: 'pointer', color: 'inherit', padding: '0 4px' }}
                onClick={() => setPaymentResult(null)}
                title="Dismiss"
              >
                <X size={16} />
              </button>
            </div>
          )}

          {isPermanentlyDeactivated ? (
            <div style={{ textAlign: 'left', marginBottom: '24px', padding: '18px', borderRadius: '10px', background: 'rgba(239, 68, 68, 0.12)', border: '1px solid rgba(239, 68, 68, 0.35)' }}>
              <div style={{ display: 'flex', gap: '12px', alignItems: 'flex-start' }}>
                <span style={{ fontSize: '24px', lineHeight: 1 }}>🚫</span>
                <div>
                  <strong style={{ display: 'block', fontSize: '15px', marginBottom: '6px', color: '#ef4444' }}>
                    Reactivation Window Expired
                  </strong>
                  <span style={{ fontSize: '13px', lineHeight: '1.6', color: 'var(--text-main)', opacity: 0.9 }}>
                    Your account has been permanently deactivated because the 10-day reactivation window has elapsed without an approved reactivation request. Further reactivation requests cannot be submitted. If you believe this is in error, please contact library administration directly.
                  </span>
                </div>
              </div>
            </div>
          ) : isPending ? (
            <div style={{ textAlign: 'left', marginBottom: '24px', padding: '18px', borderRadius: '10px', background: 'rgba(245, 158, 11, 0.12)', border: '1px solid rgba(245, 158, 11, 0.35)' }}>
              <div style={{ display: 'flex', gap: '12px', alignItems: 'flex-start' }}>
                <Clock size={22} color="#f59e0b" style={{ flexShrink: 0, marginTop: '2px' }} />
                <div>
                  <strong style={{ display: 'block', fontSize: '15px', marginBottom: '6px', color: '#f59e0b' }}>
                    Reactivation Request Under Review
                  </strong>
                  <span style={{ fontSize: '13px', lineHeight: '1.6', color: 'var(--text-main)', opacity: 0.9 }}>
                    Your reactivation request is pending administrator review
                  </span>
                </div>
              </div>
            </div>
          ) : !hasPendingFines ? (
            /* Fine settled or no fines: display strictly ONLY the Request Reactivation button */
            <div style={{ marginTop: '24px', marginBottom: '24px' }}>
              <button
                type="button"
                className="btn btn-primary btn-block"
                onClick={handleReactivateRequest}
                disabled={requestingReactivation}
                style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: '8px', padding: '16px', fontSize: '16px', fontWeight: '700' }}
              >
                {requestingReactivation ? (
                  <>
                    <RefreshCw size={18} className="spin" />
                    <span>Submitting Request...</span>
                  </>
                ) : (
                  currentUser.reactivationRequestStatus === 'REJECTED' ? 'Submit New Request' : 'Request Reactivation'
                )}
              </button>
            </div>
          ) : (
            <>
              {paymentResult && (
                <div className={`payment-result-banner banner-${paymentResult.type || 'info'}`} style={{ textAlign: 'left', marginBottom: '20px' }}>
                  <div style={{ display: 'flex', gap: '10px', alignItems: 'flex-start' }}>
                    <span style={{ fontSize: '20px', lineHeight: 1 }}>
                      {paymentResult.type === 'success' ? '✅' : paymentResult.type === 'warning' ? '⚠️' : 'ℹ️'}
                    </span>
                    <div>
                      <strong style={{ fontSize: '14px', display: 'block', marginBottom: '2px' }}>{paymentResult.title}</strong>
                      <p style={{ margin: 0, fontSize: '13px', lineHeight: 1.4 }}>{paymentResult.message}</p>
                    </div>
                  </div>
                  <button
                    type="button"
                    className="btn-link"
                    style={{ background: 'transparent', border: 'none', cursor: 'pointer', color: 'inherit', padding: '0 4px' }}
                    onClick={() => setPaymentResult(null)}
                    title="Dismiss"
                  >
                    <X size={16} />
                  </button>
                </div>
              )}

              <div style={{ textAlign: 'left', marginBottom: '20px', padding: '16px', borderRadius: '10px', background: 'rgba(239, 68, 68, 0.1)', border: '1px solid rgba(239, 68, 68, 0.25)' }}>
                <div style={{ display: 'flex', gap: '12px', alignItems: 'flex-start' }}>
                  <span style={{ fontSize: '22px', lineHeight: 1 }}>⚠️</span>
                  <div>
                    <strong style={{ display: 'block', fontSize: '15px', marginBottom: '4px', color: '#ef4444' }}>
                      Your account has been deactivated
                    </strong>
                    <span style={{ fontSize: '13px', lineHeight: '1.5', color: 'var(--text-main)', opacity: 0.9 }}>
                      Your account is deactivated due to inactivity. Please settle your overdue fine to request reactivation.
                    </span>
                  </div>
                </div>
              </div>

              {/* Outstanding Fines Section */}
              <div className="deactivated-fine-card">
                <div style={{ display: 'flex', gap: '10px', alignItems: 'center', marginBottom: '8px' }}>
                  <CreditCard size={20} color="#f59e0b" />
                  <strong style={{ fontSize: '15px', color: '#f59e0b' }}>Outstanding Fine Detected</strong>
                </div>
                <p style={{ fontSize: '13px', color: 'var(--text-main)', marginBottom: '8px' }}>
                  You have unpaid pending fines of <strong style={{ color: '#f59e0b', fontSize: '16px' }}>₹{Number(totalPendingFine).toFixed(2)}</strong>.
                </p>
                <p style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '14px' }}>
                  Please settle your pending fine to enable account reactivation:
                </p>

                {unpaidFines.length > 0 ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                    {unpaidFines.map((f, idx) => {
                      const fineAmt = f.pendingFineAmount || f.amount || 0;
                      return (
                        <div key={f.id ?? `unpaid-fine-${idx}`} className="deactivated-fine-item">
                          <div className="deactivated-fine-details">
                            <strong style={{ fontSize: '13px', color: 'var(--text-main)' }}>
                              {f.bookTitle || 'Library Book Overdue Fine'}
                            </strong>
                            {f.bookAuthor && (
                              <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>by {f.bookAuthor}</span>
                            )}
                            <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Fine #{f.id}</span>
                          </div>
                          <div className="deactivated-fine-actions">
                            <span style={{ fontSize: '14px', fontWeight: 700, color: '#f59e0b', marginRight: '6px' }}>
                              ₹{Number(fineAmt).toFixed(2)}
                            </span>
                            <button
                              type="button"
                              className="btn btn-primary btn-sm"
                              disabled={isProcessingPayment}
                              onClick={() => handlePayFine(f.id)}
                              style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '12px', padding: '6px 14px' }}
                            >
                              {isProcessingPayment ? (
                                <>
                                  <RefreshCw size={12} className="spin" />
                                  <span>Processing...</span>
                                </>
                              ) : (
                                <>
                                  <CreditCard size={12} />
                                  <span>Pay Fine</span>
                                </>
                              )}
                            </button>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                ) : (
                  <div style={{ marginTop: '10px' }}>
                    <button
                      type="button"
                      className="btn btn-primary btn-block"
                      disabled={isProcessingPayment}
                      onClick={() => {
                        if (unpaidFines[0]?.id) {
                          handlePayFine(unpaidFines[0].id);
                        } else {
                          loadFines();
                        }
                      }}
                      style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}
                    >
                      {isProcessingPayment ? (
                        <>
                          <RefreshCw size={14} className="spin" />
                          <span>Opening Razorpay...</span>
                        </>
                      ) : (
                        <>
                          <CreditCard size={16} />
                          <span>Pay Fine (₹{Number(totalPendingFine).toFixed(2)}) via Razorpay</span>
                        </>
                      )}
                    </button>
                  </div>
                )}
              </div>
            </>
          )}

          <div style={{ display: 'flex', gap: '12px', justifyContent: 'center', flexWrap: 'wrap' }}>
            <button
              type="button"
              className="btn btn-primary"
              onClick={handleRefreshStatus}
              disabled={refreshingStatus}
              style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}
            >
              <RefreshCw size={16} className={refreshingStatus ? 'spin' : ''} />
              <span>{refreshingStatus ? 'Checking...' : 'Refresh Status'}</span>
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={handleSignOut}
              style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}
            >
              <LogOut size={16} />
              <span>Sign Out</span>
            </button>
          </div>
        </div>
      </div>
    );
  }

  // Render Complete Profile Page before Dashboard if user has not linked their Aadhaar
  const isProfileIncomplete = currentUser && !currentUser.aadhaarLast4;

  if (isProfileIncomplete) {
    return (
      <div className="auth-screen">
        <div className="auth-card" style={{ maxWidth: '480px', width: '100%', padding: '36px 32px' }}>
          <div className="auth-brand" style={{ marginBottom: '16px' }}>
            <div className="brand-icon">
              <BookOpen size={28} />
            </div>
            <h1 className="brand-title">Padips</h1>
            <p className="brand-subtitle">Library Management System</p>
          </div>

          <div className="auth-header" style={{ marginBottom: '20px' }}>
            <h2 style={{ fontSize: '20px', fontWeight: '700', marginBottom: '6px' }}>Complete Your Profile</h2>
            <p className="text-muted" style={{ fontSize: '13px', margin: 0 }}>
              Please provide your details to finish setting up your library account before accessing the portal.
            </p>
          </div>

          <form onSubmit={handleCompleteProfileSubmit}>

            <div className="form-group mb-3" style={{ textAlign: 'left' }}>
              <label className="form-label" style={{ display: 'block', marginBottom: '6px', fontSize: '13px', fontWeight: 600 }}>
                Aadhaar Number <span style={{ color: 'var(--accent-primary)' }}>*</span>
              </label>
              <input
                type="text"
                maxLength={14}
                className={`form-input ${completeProfileForm.aadhaarError ? 'border-danger' : ''}`}
                style={{
                  width: '100%',
                  padding: '10px 14px',
                  borderRadius: 'var(--radius-md)',
                  border: completeProfileForm.aadhaarError ? '1px solid #ef4444' : '1px solid var(--border-medium)',
                  background: 'var(--bg-input)',
                  color: 'var(--text-main)',
                  fontSize: '14px',
                  fontFamily: 'monospace',
                  letterSpacing: '1px'
                }}
                placeholder="1234 5678 9012"
                value={completeProfileForm.aadhaarNumber}
                onChange={(e) => {
                  const raw = e.target.value.replace(/[^\d]/g, '').slice(0, 12);
                  const formatted = raw.replace(/(\d{4})(?=\d)/g, '$1 ');
                  setCompleteProfileForm({ ...completeProfileForm, aadhaarNumber: formatted, aadhaarError: '' });
                }}
                required
              />
              {completeProfileForm.aadhaarError && (
                <small className="text-danger" style={{ display: 'block', marginTop: '4px', fontSize: '12px' }}>
                  {completeProfileForm.aadhaarError}
                </small>
              )}
            </div>

            <div className="form-group mb-4" style={{ textAlign: 'left' }}>
              <label className="form-label" style={{ display: 'block', marginBottom: '6px', fontSize: '13px', fontWeight: 600 }}>
                Mobile Number <span className="text-muted" style={{ fontWeight: 'normal', fontSize: '12px' }}>(Optional)</span>
              </label>
              <div className={`phone-input-control ${completeProfileForm.phoneError ? 'has-error' : ''}`}>
                <select
                  className="phone-country-select"
                  value={completeProfileForm.countryCode}
                  onChange={e => setCompleteProfileForm({ ...completeProfileForm, countryCode: e.target.value, phoneError: '' })}
                >
                  {COUNTRY_CODES.map(c => (
                    <option key={`comp-prof-${c.code}-${c.dial}`} value={c.dial}>
                      {c.code} {c.dial}
                    </option>
                  ))}
                </select>
                <input
                  type="tel"
                  className="phone-number-field"
                  placeholder="98765 43210"
                  value={completeProfileForm.phoneNumber}
                  onChange={e => {
                    const val = e.target.value.replace(/[^\d\s-]/g, '');
                    setCompleteProfileForm(prev => ({ ...prev, phoneNumber: val, phoneError: '' }));
                  }}
                />
              </div>
              {completeProfileForm.phoneError && (
                <small className="text-danger" style={{ display: 'block', marginTop: '4px', fontSize: '12px' }}>
                  {completeProfileForm.phoneError}
                </small>
              )}
            </div>

            <button
              type="submit"
              className="btn btn-primary btn-block btn-lg mb-3"
              disabled={completeProfileForm.isSubmitting}
            >
              {completeProfileForm.isSubmitting ? (
                <>
                  <RefreshCw size={14} className="spin" />
                  <span>Saving Profile...</span>
                </>
              ) : (
                <span className="btn-text">Save & Proceed to Dashboard</span>
              )}
            </button>

            <div style={{ textAlign: 'center', marginTop: '12px' }}>
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={handleSignOut}
                style={{ fontSize: '13px', color: 'var(--text-muted)' }}
              >
                Sign Out
              </button>
            </div>
          </form>
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
              onClick={() => { setCurrentPage('members'); setMembersTab('members'); }}
            >
              <Users className="nav-icon" size={18} />
              <span style={{ flex: 1, textAlign: 'left' }}>Members</span>
              {pendingRequestsCount > 0 && (
                <span style={{
                  background: '#ef4444',
                  color: '#fff',
                  fontSize: '10px',
                  fontWeight: '700',
                  padding: '1px 6px',
                  borderRadius: '10px',
                  lineHeight: '1.2'
                }}>
                  {pendingRequestsCount}
                </span>
              )}
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
            className={`nav-link ${currentPage === 'payments' ? 'active' : ''}`}
            onClick={() => setCurrentPage('payments')}>
            <Clock className="nav-icon" size={18} />
            <span>Payment History</span>
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
            <LogOut size={16} />
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
              {currentPage === 'members' && (membersTab === 'reactivation' ? 'Account Reactivation Requests' : 'Members Directory')}
              {currentPage === 'borrow' && (userRole === 'ADMIN' ? 'Circulation Log' : 'My Loans')}
              {currentPage === 'fines' && (userRole === 'ADMIN' ? 'Fine Settlement' : 'Fines & Dues')}
              {currentPage === 'membership' && 'Digital Membership'}
              {currentPage === 'payments' && 'Payment & Refund History'}
              {currentPage === 'donations' && (userRole === 'ADMIN' ? 'Donation Management' : 'Book Donations')}
            </h1>
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
                  <button
                    className="dropdown-item"
                    onClick={() => {
                      setCurrentPage('membership');
                      setShowUserDropdown(false);
                    }}
                    style={{ display: 'flex', alignItems: 'center', gap: '8px' }}
                  >
                    <Award size={14} />
                    <span>{userRole === 'ADMIN' ? 'Membership' : 'Digital Membership'}</span>
                  </button>
                  <button
                    className="dropdown-item"
                    onClick={() => {
                      setCurrentPage('payments');
                      setShowUserDropdown(false);
                    }}
                    style={{ display: 'flex', alignItems: 'center', gap: '8px' }}
                  >
                    <Clock size={14} />
                    <span>Payment History</span>
                  </button>
                  <button
                    className="dropdown-item"
                    onClick={() => {
                      openPaymentProfileModal();
                      setShowUserDropdown(false);
                    }}
                    style={{ display: 'flex', alignItems: 'center', gap: '8px' }}
                  >
                    <Landmark size={14} />
                    <span>Bank Account Details</span>
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
          {paymentResult && (
            <div className={`payment-result-banner banner-${paymentResult.type || 'info'}`}>
              <div style={{ flex: 1 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                  <Shield size={18} />
                  <strong style={{ fontSize: '15px' }}>{paymentResult.title}</strong>
                </div>
                <p style={{ margin: 0, fontSize: '14px', lineHeight: 1.5 }}>{paymentResult.message}</p>
              </div>
              <button
                type="button"
                onClick={() => setPaymentResult(null)}
                style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'inherit', padding: '4px', marginLeft: '12px' }}
                aria-label="Dismiss payment notification"
              >
                <X size={18} />
              </button>
            </div>
          )}
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
                      <h2 style={{ fontSize: '22px', fontWeight: 700, marginTop: '6px', marginBottom: '4px' }}>Welcome to Padips</h2>
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
                        <span className="metric-value text-danger">₹{fineTotalBalance.toFixed(2)}</span>
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
                                      uuid: b.uuid,
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
                                  <button className="btn btn-danger btn-sm" onClick={() => handleDeleteBook(b.uuid || b.id, b.title)}>Del</button>
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
              {/* Tab Navigation between Members Directory and Reactivation Requests */}
              <div style={{
                display: 'flex',
                gap: '10px',
                marginBottom: '20px',
                borderBottom: '1px solid var(--border-subtle)',
                paddingBottom: '12px'
              }}>
                <button
                  type="button"
                  className={`btn ${membersTab === 'members' ? 'btn-primary' : 'btn-ghost'}`}
                  onClick={() => setMembersTab('members')}
                  style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', borderRadius: '8px' }}
                >
                  <Users size={16} />
                  <span>All Members</span>
                  <span style={{
                    background: membersTab === 'members' ? 'rgba(255,255,255,0.25)' : 'rgba(255,255,255,0.08)',
                    padding: '2px 8px',
                    borderRadius: '12px',
                    fontSize: '11px',
                    fontWeight: '600'
                  }}>
                    {members.length}
                  </span>
                </button>

                <button
                  type="button"
                  className={`btn ${membersTab === 'reactivation' ? 'btn-primary' : 'btn-ghost'}`}
                  onClick={() => setMembersTab('reactivation')}
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '8px',
                    borderRadius: '8px',
                    border: membersTab !== 'reactivation' && pendingRequestsCount > 0 ? '1px solid rgba(245, 158, 11, 0.4)' : undefined,
                    background: membersTab !== 'reactivation' && pendingRequestsCount > 0 ? 'rgba(245, 158, 11, 0.08)' : undefined
                  }}
                >
                  <Bell size={16} style={{ color: pendingRequestsCount > 0 && membersTab !== 'reactivation' ? '#f59e0b' : 'inherit' }} />
                  <span>Reactivation Requests</span>
                  <span style={{
                    background: pendingRequestsCount > 0 ? (membersTab === 'reactivation' ? 'rgba(255,255,255,0.25)' : '#f59e0b') : 'rgba(255,255,255,0.08)',
                    color: pendingRequestsCount > 0 && membersTab !== 'reactivation' ? '#fff' : 'inherit',
                    padding: '2px 8px',
                    borderRadius: '12px',
                    fontSize: '11px',
                    fontWeight: '700'
                  }}>
                    {pendingRequestsCount}
                  </span>
                </button>
              </div>

              {/* VIEW 1: ALL MEMBERS DIRECTORY */}
              {membersTab === 'members' && (
                <div>
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

                    <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>

                      <button className="btn btn-primary" onClick={() => { setUserForm({ id: '', name: '', email: '', password: '', countryCode: '+1', phoneNumber: '', phoneError: '' }); setActiveModal('createUser'); }}>
                        <Plus size={16} />
                        <span>Register Member</span>
                      </button>
                    </div>
                  </div>

                  <div className="table-container mt-4">
                    <table className="data-table">
                      <thead>
                        <tr>
                          <th width="60">ID</th>
                          <th>Member Name</th>
                          <th>Email Address</th>
                          <th width="150">Aadhaar (UIDAI)</th>
                          <th width="150">Phone Number</th>
                          <th width="140">Status</th>
                          <th width="130">Reward Points</th>
                          <th width="100" className="text-right">Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {members.length === 0 ? (
                          <tr><td colSpan="8" className="empty-cell">No members found.</td></tr>
                        ) : (
                          members.map((m, idx) => (
                            <tr key={m.id ?? m.uuid ?? m.email ?? `member-${idx}`}>
                              <td>#{m.id || '—'}</td>
                              <td><strong>{m.name || m.username}</strong></td>
                              <td>{m.email}</td>
                              <td>
                                {m.aadhaarLast4 ? (
                                  <span style={{ fontFamily: 'monospace', fontSize: '12px', background: 'var(--bg-subtle)', padding: '2px 6px', borderRadius: '4px', letterSpacing: '0.5px' }}>
                                    XXXX XXXX {m.aadhaarLast4}
                                  </span>
                                ) : (
                                  <span className="text-subtle" style={{ fontSize: '12px' }}>—</span>
                                )}
                              </td>
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
                                {(m.reactivationRequestStatus === 'PENDING' || m.reactivationRequested) ? (
                                  <div style={{ display: 'flex', flexDirection: 'column', gap: '2px', alignItems: 'flex-start' }}>
                                    <span style={{ color: '#ef4444', fontWeight: '600', fontSize: '13px' }}>
                                      Deactivated
                                    </span>
                                    <span style={{ color: '#f59e0b', fontWeight: '500', fontSize: '11px' }}>
                                      Pending Review
                                    </span>
                                  </div>
                                ) : String(m.status || '').toUpperCase() === 'PERMANENTLY_DEACTIVATED' ? (
                                  <div style={{ display: 'flex', flexDirection: 'column', gap: '2px', alignItems: 'flex-start' }}>
                                    <span style={{ color: '#ef4444', fontWeight: '700', fontSize: '12px', background: 'rgba(239, 68, 68, 0.12)', padding: '2px 8px', borderRadius: '4px', border: '1px solid rgba(239, 68, 68, 0.25)' }}>
                                      Permanently Deactivated
                                    </span>
                                    {m.reactivationRequestStatus === 'REJECTED' && (
                                      <span style={{ color: '#f87171', fontWeight: '500', fontSize: '11px' }}>
                                        Request Rejected
                                      </span>
                                    )}
                                  </div>
                                ) : String(m.status || '').toUpperCase() === 'DEACTIVATED' ? (
                                  <div style={{ display: 'flex', flexDirection: 'column', gap: '2px', alignItems: 'flex-start' }}>
                                    <span style={{ color: '#ef4444', fontWeight: '600', fontSize: '13px' }}>
                                      Deactivated
                                    </span>
                                    {m.reactivationRequestStatus === 'REJECTED' ? (
                                      <span style={{ color: '#f87171', fontWeight: '500', fontSize: '11px' }}>
                                        Request Rejected
                                      </span>
                                    ) : null}
                                  </div>
                                ) : (
                                  <div style={{ display: 'flex', flexDirection: 'column', gap: '2px', alignItems: 'flex-start' }}>
                                    <span style={{ color: '#10b981', fontWeight: '600', fontSize: '13px' }}>
                                      Active
                                    </span>
                                    {m.reactivationRequestStatus === 'APPROVED' && (
                                      <span style={{ color: '#38bdf8', fontWeight: '500', fontSize: '11px' }}>
                                        Reactivated
                                      </span>
                                    )}
                                  </div>
                                )}
                              </td>
                              <td>
                                <span style={{ color: '#eab308', fontWeight: '600', fontSize: '13px' }}>
                                  🏆 {m.rewardPoints || 0} pts
                                </span>
                              </td>
                              <td className="text-right">
                                <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end', alignItems: 'center' }}>
                                  <button
                                    className="btn btn-secondary btn-sm"
                                    style={{ padding: '5px 8px', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}
                                    title="Edit Member"
                                    aria-label="Edit Member"
                                    onClick={() => {
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
                                    }}
                                  >
                                    <Edit2 size={14} />
                                  </button>
                                  <button
                                    className="btn btn-danger btn-sm"
                                    style={{ padding: '5px 8px', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}
                                    title="Delete Member"
                                    aria-label="Delete Member"
                                    onClick={() => handleDeleteUser(m.id, m.name || m.email)}
                                  >
                                    <Trash2 size={14} />
                                  </button>
                                </div>
                              </td>
                            </tr>
                          ))
                        )}
                      </tbody>
                    </table>
                  </div>

                  <div className="pagination-footer mt-4">
                    <span className="pagination-info">Showing page {membersPage + 1} of {membersTotalPages}</span>
                    <div className="pagination-buttons">
                      <button className="btn btn-secondary btn-sm" disabled={membersPage <= 0} onClick={() => { setMembersPage(prev => prev - 1); loadMembers(membersPage - 1, memberSearchQuery, memberSortBy, memberSortDir); }}>&larr; Previous</button>
                      <button className="btn btn-secondary btn-sm" disabled={membersPage >= membersTotalPages - 1} onClick={() => { setMembersPage(prev => prev + 1); loadMembers(membersPage + 1, memberSearchQuery, memberSortBy, memberSortDir); }}>Next &rarr;</button>
                    </div>
                  </div>
                </div>
              )}

              {/* VIEW 2: REACTIVATION REQUESTS (FULL PAGE VIEW, NOT A POPUP) */}
              {membersTab === 'reactivation' && (
                <div>
                  {/* Summary Metric Cards */}
                  <div style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
                    gap: '16px',
                    marginBottom: '20px'
                  }}>
                    <div style={{
                      background: 'var(--bg-surface, #1e293b)',
                      border: '1px solid var(--border-subtle)',
                      borderRadius: '12px',
                      padding: '16px 20px',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '14px'
                    }}>
                      <div style={{
                        width: '42px',
                        height: '42px',
                        borderRadius: '10px',
                        background: 'rgba(59, 130, 246, 0.15)',
                        color: '#3b82f6',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center'
                      }}>
                        <Users size={22} />
                      </div>
                      <div>
                        <div style={{ fontSize: '12px', color: 'var(--text-muted)', fontWeight: '500' }}>Total Requests</div>
                        <div style={{ fontSize: '20px', fontWeight: '700', color: 'var(--text-main)' }}>{reactivationRequests.length}</div>
                      </div>
                    </div>

                    <div style={{
                      background: 'var(--bg-surface, #1e293b)',
                      border: pendingRequestsCount > 0 ? '1px solid rgba(245, 158, 11, 0.5)' : '1px solid var(--border-subtle)',
                      borderRadius: '12px',
                      padding: '16px 20px',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '14px'
                    }}>
                      <div style={{
                        width: '42px',
                        height: '42px',
                        borderRadius: '10px',
                        background: 'rgba(245, 158, 11, 0.15)',
                        color: '#f59e0b',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center'
                      }}>
                        <Bell size={22} />
                      </div>
                      <div>
                        <div style={{ fontSize: '12px', color: 'var(--text-muted)', fontWeight: '500' }}>Pending Review</div>
                        <div style={{ fontSize: '20px', fontWeight: '700', color: '#f59e0b' }}>{pendingRequestsCount}</div>
                      </div>
                    </div>

                    <div style={{
                      background: 'var(--bg-surface, #1e293b)',
                      border: '1px solid var(--border-subtle)',
                      borderRadius: '12px',
                      padding: '16px 20px',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '14px'
                    }}>
                      <div style={{
                        width: '42px',
                        height: '42px',
                        borderRadius: '10px',
                        background: 'rgba(16, 185, 129, 0.15)',
                        color: '#10b981',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center'
                      }}>
                        <Check size={22} />
                      </div>
                      <div>
                        <div style={{ fontSize: '12px', color: 'var(--text-muted)', fontWeight: '500' }}>Approved</div>
                        <div style={{ fontSize: '20px', fontWeight: '700', color: '#10b981' }}>{approvedRequestsCount}</div>
                      </div>
                    </div>

                    <div style={{
                      background: 'var(--bg-surface, #1e293b)',
                      border: '1px solid var(--border-subtle)',
                      borderRadius: '12px',
                      padding: '16px 20px',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '14px'
                    }}>
                      <div style={{
                        width: '42px',
                        height: '42px',
                        borderRadius: '10px',
                        background: 'rgba(239, 68, 68, 0.15)',
                        color: '#ef4444',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center'
                      }}>
                        <X size={22} />
                      </div>
                      <div>
                        <div style={{ fontSize: '12px', color: 'var(--text-muted)', fontWeight: '500' }}>Rejected</div>
                        <div style={{ fontSize: '20px', fontWeight: '700', color: '#ef4444' }}>{rejectedRequestsCount}</div>
                      </div>
                    </div>
                  </div>

                  {/* Filter and Action Bar */}
                  <div className="action-bar mb-4" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
                    <div className="filter-controls" style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
                      <div className="search-input-group">
                        <Search size={16} />
                        <input
                          type="text"
                          placeholder="Search request by member name or email..."
                          value={reactivationSearchQuery}
                          onChange={(e) => setReactivationSearchQuery(e.target.value)}
                        />
                      </div>
                      {reactivationSearchQuery && (
                        <button className="btn btn-ghost" onClick={() => setReactivationSearchQuery('')}>Clear</button>
                      )}

                      <div style={{ display: 'flex', gap: '6px', alignItems: 'center', marginLeft: '8px', flexWrap: 'wrap' }}>
                        {[
                          { id: 'PENDING', label: 'Pending', count: pendingRequestsCount, color: '#f59e0b' },
                          { id: 'APPROVED', label: 'Approved', count: approvedRequestsCount, color: '#10b981' },
                          { id: 'REJECTED', label: 'Rejected', count: rejectedRequestsCount, color: '#ef4444' },
                          { id: 'ALL', label: 'All Requests', count: reactivationRequests.length, color: 'var(--accent-primary)' }
                        ].map(tab => {
                          const isActive = reactivationTab === tab.id;
                          return (
                            <button
                              key={tab.id}
                              type="button"
                              className={`btn btn-sm ${isActive ? 'btn-primary' : 'btn-secondary'}`}
                              onClick={() => setReactivationTab(tab.id)}
                              style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                            >
                              <span>{tab.label}</span>
                              <span style={{
                                background: isActive ? 'rgba(255,255,255,0.25)' : 'rgba(255,255,255,0.1)',
                                padding: '1px 6px',
                                borderRadius: '10px',
                                fontSize: '11px',
                                fontWeight: '600'
                              }}>
                                {tab.count}
                              </span>
                            </button>
                          );
                        })}
                      </div>
                    </div>

                    <div>
                      <button
                        type="button"
                        className="btn btn-secondary"
                        onClick={() => setMembersTab('members')}
                        style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                      >
                        &larr; Back to All Members
                      </button>
                    </div>
                  </div>

                  {/* Reactivation Requests Data Table */}
                  <div className="table-container mt-4">
                    <table className="data-table">
                      <thead>
                        <tr>
                          <th width="60">ID</th>
                          <th>Member Name</th>
                          <th>Email Address</th>
                          <th width="150">Phone Number</th>
                          <th width="140">Account Status</th>
                          <th width="130">Reward Points</th>
                          <th width="140">Request Status</th>
                          <th width="200" className="text-right">Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {(() => {
                          const filtered = reactivationRequests.filter(req => {
                            const reqStatus = (req.reactivationRequestStatus || (req.reactivationRequested ? 'PENDING' : 'NONE')).toUpperCase();
                            const matchesTab =
                              reactivationTab === 'ALL' ? true :
                                reactivationTab === 'PENDING' ? (reqStatus === 'PENDING' || (req.reactivationRequested && reqStatus !== 'APPROVED' && reqStatus !== 'REJECTED')) :
                                  reactivationTab === 'APPROVED' ? reqStatus === 'APPROVED' :
                                    reactivationTab === 'REJECTED' ? reqStatus === 'REJECTED' : true;

                            if (!matchesTab) return false;

                            if (reactivationSearchQuery.trim()) {
                              const q = reactivationSearchQuery.toLowerCase();
                              const name = (req.name || '').toLowerCase();
                              const email = (req.email || '').toLowerCase();
                              return name.includes(q) || email.includes(q);
                            }
                            return true;
                          });

                          if (filtered.length === 0) {
                            return (
                              <tr>
                                <td colSpan="8" className="empty-cell" style={{ padding: '48px 20px', textAlign: 'center' }}>
                                  <div style={{ fontSize: '36px', marginBottom: '10px' }}>
                                    {reactivationTab === 'PENDING' ? '✨' : reactivationTab === 'APPROVED' ? '✅' : reactivationTab === 'REJECTED' ? '📋' : '📭'}
                                  </div>
                                  <h4 style={{ color: 'var(--text-main)', fontSize: '15px', fontWeight: '600', margin: '0 0 6px' }}>
                                    {reactivationTab === 'ALL'
                                      ? 'No Reactivation Requests Found'
                                      : `No ${reactivationTab.charAt(0) + reactivationTab.slice(1).toLowerCase()} Reactivation Requests`}
                                  </h4>
                                  <p style={{ color: 'var(--text-muted)', fontSize: '13px', margin: 0, maxWidth: '440px', marginInline: 'auto' }}>
                                    {reactivationTab === 'PENDING'
                                      ? ' No pending request'
                                      : reactivationTab === 'APPROVED'
                                        ? 'No reactivation requests have been approved yet.'
                                        : reactivationTab === 'REJECTED'
                                          ? 'No account reactivation requests are currently marked as rejected.'
                                          : 'There are currently no account reactivation requests recorded in the system.'}
                                  </p>
                                </td>
                              </tr>
                            );
                          }

                          return filtered.map((req, idx) => {
                            const reqStatus = (req.reactivationRequestStatus || (req.reactivationRequested ? 'PENDING' : 'NONE')).toUpperCase();
                            const isPending = reqStatus === 'PENDING' || (req.reactivationRequested && reqStatus !== 'APPROVED' && reqStatus !== 'REJECTED');
                            const isApproved = reqStatus === 'APPROVED';
                            const isRejected = reqStatus === 'REJECTED';

                            return (
                              <tr key={req.id ?? req.uuid ?? req.email ?? `req-${idx}`}>
                                <td>#{req.id || '—'}</td>
                                <td><strong>{req.name || req.email}</strong></td>
                                <td>{req.email}</td>
                                <td>
                                  {req.phone ? (
                                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '5px', fontSize: '13px', fontWeight: '500' }}>
                                      <Phone size={13} style={{ color: 'var(--accent-primary)', opacity: 0.8 }} />
                                      <code>{req.phone}</code>
                                    </span>
                                  ) : (
                                    <span className="text-subtle" style={{ fontSize: '12px' }}>—</span>
                                  )}
                                </td>
                                <td>
                                  <span style={{
                                    color: String(req.status || '').toUpperCase() === 'ACTIVE' ? '#10b981' :
                                      String(req.status || '').toUpperCase() === 'PERMANENTLY_DEACTIVATED' ? '#dc2626' : '#ef4444',
                                    fontWeight: '600',
                                    fontSize: '13px'
                                  }}>
                                    {String(req.status || '').toUpperCase() === 'ACTIVE' ? 'Active' :
                                      String(req.status || '').toUpperCase() === 'PERMANENTLY_DEACTIVATED' ? 'Permanently Deactivated' : 'Deactivated'}
                                  </span>
                                </td>
                                <td>
                                  <span style={{ color: '#eab308', fontWeight: '600', fontSize: '13px' }}>
                                    🏆 {req.rewardPoints || 0} pts
                                  </span>
                                </td>
                                <td>
                                  {isPending && (
                                    <span style={{ color: '#f59e0b', fontWeight: '600', fontSize: '13px' }}>
                                      Pending Review
                                    </span>
                                  )}
                                  {isApproved && (
                                    <span style={{ color: '#10b981', fontWeight: '600', fontSize: '13px' }}>
                                      Approved
                                    </span>
                                  )}
                                  {isRejected && (
                                    <span style={{ color: '#ef4444', fontWeight: '600', fontSize: '13px' }}>
                                      Rejected
                                    </span>
                                  )}
                                </td>
                                <td className="text-right">
                                  <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end', flexWrap: 'wrap', alignItems: 'center' }}>
                                    {isPending && (
                                      <>
                                        <button
                                          type="button"
                                          className="btn btn-sm"
                                          style={{ background: 'var(--accent-primary, #3b82f6)', color: '#fff', fontSize: '11px', padding: '4px 10px', fontWeight: '600', display: 'inline-flex', alignItems: 'center', gap: '4px' }}
                                          title="Approve reactivation request"
                                          onClick={() => handleApproveReactivation(req.id, req.name || req.email)}
                                        >
                                          <Check size={13} />
                                          <span>Approve</span>
                                        </button>
                                        <button
                                          type="button"
                                          className="btn btn-sm btn-danger"
                                          style={{ fontSize: '11px', padding: '4px 10px', fontWeight: '600', display: 'inline-flex', alignItems: 'center', gap: '4px' }}
                                          title="Reject reactivation request"
                                          onClick={() => handleRejectReactivation(req.id, req.name || req.email)}
                                        >
                                          <X size={13} />
                                          <span>Reject</span>
                                        </button>
                                      </>
                                    )}
                                    {isRejected && (
                                      <button
                                        type="button"
                                        className="btn btn-sm"
                                        style={{ background: 'var(--accent-primary, #3b82f6)', color: '#fff', fontSize: '11px', padding: '4px 10px', fontWeight: '600', display: 'inline-flex', alignItems: 'center', gap: '4px' }}
                                        title="Approve previously rejected request"
                                        onClick={() => handleApproveReactivation(req.id, req.name || req.email)}
                                      >
                                        <Check size={13} />
                                        <span>Approve</span>
                                      </button>
                                    )}
                                    {isApproved && (
                                      <span style={{ fontSize: '12px', color: '#10b981', fontWeight: '600', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                                        <Check size={13} />
                                        <span>Active</span>
                                      </span>
                                    )}
                                  </div>
                                </td>
                              </tr>
                            );
                          });
                        })()}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
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
                    <span className="summary-amount text-danger">₹{fineTotalBalance.toFixed(2)}</span>
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
                              <td><strong>₹{(f.amount || f.pendingFineAmount || 0).toFixed(2)}</strong></td>
                              <td>
                                <span style={{ color: f.status === 'PAID' ? '#10b981' : '#ef4444', fontWeight: 600 }}>
                                  {f.status}
                                </span>
                              </td>
                              <td className="text-right">
                                {f.status === 'UNPAID' || f.status === 'PENDING' ? (
                                  <button
                                    className="btn btn-primary btn-sm"
                                    disabled={isProcessingPayment}
                                    onClick={() => handlePayFine(f.id)}
                                  >
                                    Pay Fine (₹{(f.amount || f.pendingFineAmount || 0).toFixed(2)})
                                  </button>
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
                membership.isSigned ? (
                  /* Step 3: Security Deposit Payment via Razorpay Checkout */
                  <div className="section-card max-w-2xl" style={{ margin: '0 auto' }}>
                    <div className="payment-steps-row">
                      <div className="payment-step-badge completed"><span>✓</span> 1. Application</div>
                      <div className="payment-step-badge completed"><span>✓</span> 2. Agreement Signed</div>
                      <div className="payment-step-badge active"><span>3</span> Pay Security Deposit</div>
                    </div>

                    <div className="section-header text-center">
                      <div className="brand-badge mb-4" style={{ margin: '0 auto', display: 'inline-flex', width: '56px', height: '56px', borderRadius: '50%', background: 'rgba(99, 102, 241, 0.15)', alignItems: 'center', justifyContent: 'center', color: '#818cf8' }}>
                        <CreditCard size={28} />
                      </div>
                      <h3>Activate 1-Year Library Membership</h3>
                      <p className="section-desc">Agreement signed successfully! Complete your one-time refundable security deposit to activate your digital membership for 1 year.</p>
                    </div>

                    <div className="payment-order-card mt-6">
                      <div className="payment-order-item">
                        <span>Membership Period</span>
                        <strong>1 Year (365 Days)</strong>
                      </div>
                      <div className="payment-order-item">
                        <span>Agreement Status</span>
                        <span style={{ color: '#22c55e', fontWeight: 600 }}>✓ Signed & Stored</span>
                      </div>
                      <div className="payment-order-item">
                        <span>Refundable Security Deposit</span>
                        <strong style={{ fontSize: '18px', color: '#6366f1' }}>₹500.00</strong>
                      </div>
                      <div className="payment-order-item" style={{ borderBottom: 'none', paddingBottom: 0 }}>
                        <span className="text-muted text-xs">
                          * 100% refundable upon membership cancellation (minus any unpaid fines) or permanent deactivation.
                        </span>
                      </div>
                    </div>
                    <button
                      className="btn btn-primary btn-block mt-6"
                      disabled={isProcessingPayment}
                      onClick={handlePayMembership}
                      style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px', padding: '14px', fontSize: '15px' }}
                    >
                      {isProcessingPayment ? (
                        <>
                          <RefreshCw className="animate-spin" size={18} />
                          Opening Razorpay Gateway...
                        </>
                      ) : (
                        <>
                          <CreditCard size={18} />
                          Pay ₹500 via Razorpay Checkout
                        </>
                      )}
                    </button>

                    <p className="text-muted text-center text-xs mt-3">
                      Razorpay Test Mode. Supports UPI, Cards, NetBanking, and Wallets.
                    </p>
                  </div>
                ) : (
                  /* Step 2: Review & Sign Agreement */
                  <div className="section-card">
                    <div className="payment-steps-row">
                      <div className="payment-step-badge completed"><span>✓</span> 1. Application</div>
                      <div className="payment-step-badge active"><span>2</span> Sign Agreement</div>
                      <div className="payment-step-badge"><span>3</span> Pay Security Deposit</div>
                    </div>

                    <div className="section-header">
                      <h3>Review & Sign Agreement</h3>
                      <p className="section-desc">Please review the agreement and upload your signature PNG to proceed to activation</p>
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
                          Sign Agreement & Proceed to Payment
                        </button>
                      </div>
                    </div>
                  </div>
                )
              ) : (
                <div>
                  <div className="grid-2col">
                    <div className="membership-card-badge" style={{ background: 'linear-gradient(135deg, #1e293b 0%, #0f172a 100%)', border: '1px solid #334155', borderRadius: '16px', padding: '24px', color: '#ffffff', minHeight: '220px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between', position: 'relative', overflow: 'hidden' }}>
                      <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                        <div>
                          <h4 style={{ fontSize: '18px', fontWeight: 'bold', letterSpacing: '0.05em', color: '#38bdf8', margin: 0 }}>PADIPS</h4>
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

                      <button className="btn btn-secondary btn-block mt-3" onClick={() => setCurrentPage('payments')}>
                        <Clock size={16} style={{ marginRight: '8px' }} />
                        View Payment History
                      </button>

                      <button className="btn btn-danger btn-block mt-3" onClick={handleCancelMembership}>
                        Cancel Membership
                      </button>

                      {/* Saved Bank Details Card */}
                      {paymentProfile ? (
                        <div className="amazon-payment-box mt-4" style={{ textAlign: 'left' }}>
                          <div className="amazon-section-title" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                              <Landmark size={18} color="var(--accent-primary)" />
                              <span>Linked Refund Bank Account</span>
                            </div>
                            <button
                              type="button"
                              className="btn btn-link btn-sm"
                              style={{ fontSize: '12px', padding: 0, color: 'var(--accent-primary)', textDecoration: 'underline' }}
                              onClick={() => openPaymentProfileModal(true)}
                            >
                              Edit Details
                            </button>
                          </div>
                          <div className="saved-profile-card" style={{ marginBottom: 0 }}>
                            <div className="saved-profile-info">
                              <div className="saved-profile-icon">
                                <Landmark size={20} />
                              </div>
                              <div className="saved-profile-details">
                                <strong className="saved-profile-title">
                                  {paymentProfile.bankName}
                                </strong>
                                <span className="saved-profile-sub">
                                  {paymentProfile.accountHolderName} • A/C: {paymentProfile.maskedAccountNumber || '••••••••'} • IFSC: {paymentProfile.ifscCode}
                                </span>
                              </div>
                            </div>
                          </div>
                        </div>
                      ) : (
                        <div className="amazon-payment-box mt-4" style={{ textAlign: 'left' }}>
                          <div className="amazon-section-title" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <Landmark size={18} color="var(--accent-primary)" />
                            <span>Linked Refund Bank Account</span>
                          </div>
                          <button
                            type="button"
                            className="btn btn-secondary btn-sm"
                            onClick={() => openPaymentProfileModal(true)}
                            style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                          >
                            <Plus size={14} />
                            <span>Add Bank Details</span>
                          </button>
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* PAGE: PAYMENT & REFUND HISTORY */}
          {currentPage === 'payments' && (
            <div className="page-pane active">
              {/* Summary Metrics Cards */}
              <div className="metrics-grid mb-6" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))' }}>
                <div className="metric-card">
                  <div className="metric-icon" style={{ background: 'rgba(34, 197, 94, 0.15)', color: '#22c55e' }}>
                    <CreditCard size={24} />
                  </div>
                  <div>
                    <span className="text-muted text-xs" style={{ textTransform: 'uppercase', letterSpacing: '0.5px' }}>Total Paid</span>
                    <h3 style={{ fontSize: '24px', fontWeight: 700, margin: '2px 0 0 0' }}>
                      ₹{((paymentHistory.totalPaid || 0) / 100).toFixed(2)}
                    </h3>
                    <small className="text-muted" style={{ fontSize: '11px' }}>All successful transactions</small>
                  </div>
                </div>

                <div className="metric-card">
                  <div className="metric-icon" style={{ background: 'rgba(249, 115, 22, 0.15)', color: '#f97316' }}>
                    <RefreshCw size={24} />
                  </div>
                  <div>
                    <span className="text-muted text-xs" style={{ textTransform: 'uppercase', letterSpacing: '0.5px' }}>Total Refunded</span>
                    <h3 style={{ fontSize: '24px', fontWeight: 700, margin: '2px 0 0 0' }}>
                      ₹{((paymentHistory.totalRefunded || 0) / 100).toFixed(2)}
                    </h3>
                    <small className="text-muted" style={{ fontSize: '11px' }}>Deposits & dues returned</small>
                  </div>
                </div>

                <div className="metric-card">
                  <div className="metric-icon" style={{ background: 'rgba(99, 102, 241, 0.15)', color: '#6366f1' }}>
                    <Landmark size={24} />
                  </div>
                  <div>
                    <span className="text-muted text-xs" style={{ textTransform: 'uppercase', letterSpacing: '0.5px' }}>Net Paid</span>
                    <h3 style={{ fontSize: '24px', fontWeight: 700, margin: '2px 0 0 0' }}>
                      ₹{((paymentHistory.netPaid || 0) / 100).toFixed(2)}
                    </h3>
                    <small className="text-muted" style={{ fontSize: '11px' }}>Total Paid minus Refunded</small>
                  </div>
                </div>

                <div className="metric-card">
                  <div className="metric-icon" style={{ background: 'rgba(16, 185, 129, 0.15)', color: '#10b981' }}>
                    <Check size={24} />
                  </div>
                  <div>
                    <span className="text-muted text-xs" style={{ textTransform: 'uppercase', letterSpacing: '0.5px' }}>Successful Count</span>
                    <h3 style={{ fontSize: '24px', fontWeight: 700, margin: '2px 0 0 0' }}>
                      {paymentHistory.successfulTransactionCount || 0}
                    </h3>
                    <small className="text-muted" style={{ fontSize: '11px' }}>Settled payments & refunds</small>
                  </div>
                </div>
              </div>

              {/* Transactions Section Card */}
              <div className="section-card">
                <div className="section-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
                  <div>
                    <h3>Transactions & Invoices</h3>
                  </div>

                  <button
                    type="button"
                    className="btn btn-secondary btn-sm"
                    onClick={() => loadPaymentHistory(paymentHistoryPage, paymentHistoryType, paymentHistoryStatus, paymentHistorySearch)}
                    disabled={isLoadingPaymentHistory}
                    style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                  >
                    <RefreshCw size={14} className={isLoadingPaymentHistory ? 'animate-spin' : ''} />
                    <span>Refresh</span>
                  </button>
                </div>

                {/* Filter and Search Row */}
                <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', alignItems: 'center', margin: '20px 0' }}>
                  <div style={{ flex: '1 1 240px', position: 'relative' }}>
                    <Search size={16} style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
                    <input
                      type="text"
                      className="form-input"
                      placeholder="Search by Payment ID, Order ID, or Ref..."
                      value={paymentHistorySearch}
                      onChange={e => {
                        setPaymentHistorySearch(e.target.value);
                        setPaymentHistoryPage(0);
                      }}
                      onKeyDown={e => {
                        if (e.key === 'Enter') {
                          loadPaymentHistory(0, paymentHistoryType, paymentHistoryStatus, e.target.value);
                        }
                      }}
                      style={{ paddingLeft: '36px' }}
                    />
                  </div>

                  <div style={{ width: '180px' }}>
                    <select
                      className="form-input"
                      value={paymentHistoryType}
                      onChange={e => {
                        setPaymentHistoryType(e.target.value);
                        setPaymentHistoryPage(0);
                        loadPaymentHistory(0, e.target.value, paymentHistoryStatus, paymentHistorySearch);
                      }}
                    >
                      <option value="">All Payment Types</option>
                      <option value="MEMBERSHIP">Membership Fee</option>
                      <option value="FINE">Overdue Fine</option>
                    </select>
                  </div>

                  <div style={{ width: '190px' }}>
                    <select
                      className="form-input"
                      value={paymentHistoryStatus}
                      onChange={e => {
                        setPaymentHistoryStatus(e.target.value);
                        setPaymentHistoryPage(0);
                        loadPaymentHistory(0, paymentHistoryType, e.target.value, paymentHistorySearch);
                      }}
                    >
                      <option value="">All Statuses</option>
                      <option value="SUCCESS">Success</option>
                      <option value="FAILED">Failed</option>
                    </select>
                  </div>
                </div>

                {/* Table */}
                <div className="table-responsive">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Date & Time</th>
                        <th>Type</th>
                        <th>Reference / Razorpay ID</th>
                        <th>Amount</th>
                        <th>Status</th>
                        <th>Refund Details</th>
                        <th>Receipt / Document</th>
                      </tr>
                    </thead>
                    <tbody>
                      {isLoadingPaymentHistory ? (
                        <tr>
                          <td colSpan="7" style={{ textAlign: 'center', padding: '36px' }}>
                            <div style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', color: 'var(--text-muted)' }}>
                              <RefreshCw size={18} className="animate-spin" />
                              <span>Loading payment history...</span>
                            </div>
                          </td>
                        </tr>
                      ) : !paymentHistory.content || paymentHistory.content.length === 0 ? (
                        <tr>
                          <td colSpan="7" style={{ textAlign: 'center', padding: '48px 24px' }}>
                            <Clock size={36} color="var(--text-muted)" style={{ margin: '0 auto 12px' }} />
                            <h4 style={{ margin: '0 0 4px', fontSize: '15px' }}>No Payment Records Found</h4>
                            <p className="text-muted" style={{ fontSize: '13px', margin: 0 }}>
                              {paymentHistorySearch || paymentHistoryType || paymentHistoryStatus
                                ? 'No transactions match the selected filters.'
                                : 'You have not made any payments or refund transactions yet.'}
                            </p>
                          </td>
                        </tr>
                      ) : (
                        paymentHistory.content.map((item) => {
                          const isExpanded = expandedPaymentUuid === item.uuid;
                          const hasRefunds = item.refunds && item.refunds.length > 0;
                          return (
                            <React.Fragment key={item.uuid || item.id}>
                              <tr>
                                <td>
                                  <div style={{ display: 'flex', flexDirection: 'column' }}>
                                    <strong style={{ fontSize: '13px' }}>
                                      {item.createdAt ? new Date(item.createdAt).toLocaleDateString() : 'N/A'}
                                    </strong>
                                    <span className="text-muted" style={{ fontSize: '11px' }}>
                                      {item.createdAt ? new Date(item.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}
                                    </span>
                                  </div>
                                </td>

                                <td>
                                  {item.transactionType === 'REFUND' ? (
                                    <span className="badge" style={{ background: 'rgba(249, 115, 22, 0.15)', color: '#f97316' }}>
                                      Refund
                                    </span>
                                  ) : item.paymentType === 'MEMBERSHIP' ? (
                                    <span className="badge" style={{ background: 'rgba(99, 102, 241, 0.15)', color: '#818cf8' }}>
                                      Membership Fee
                                    </span>
                                  ) : (
                                    <span className="badge" style={{ background: 'rgba(234, 179, 8, 0.15)', color: '#eab308' }}>
                                      Overdue Fine
                                    </span>
                                  )}
                                </td>

                                <td>
                                  <div style={{ display: 'flex', flexDirection: 'column' }}>
                                    <span style={{ fontFamily: 'monospace', fontSize: '12px', fontWeight: 600 }}>
                                      {item.razorpayPaymentId || item.razorpayRefundId || item.razorpayOrderId || item.uuid?.slice(0, 16)}
                                    </span>
                                    {item.razorpayOrderId && item.razorpayPaymentId && (
                                      <small className="text-muted" style={{ fontSize: '11px', fontFamily: 'monospace' }}>
                                        Order: {item.razorpayOrderId}
                                      </small>
                                    )}
                                  </div>
                                </td>

                                <td>
                                  <strong style={{
                                    fontSize: '14px',
                                    color: item.transactionType === 'REFUND' ? '#f97316' : '#22c55e'
                                  }}>
                                    {item.transactionType === 'REFUND' ? '-' : '+'}₹{((item.amount || 0) / 100).toFixed(2)}
                                  </strong>
                                </td>

                                <td>
                                  {item.status === 'SUCCESS' && (
                                    <span className="badge badge-success">Success</span>
                                  )}
                                  {item.status === 'FAILED' && (
                                    <span className="badge badge-danger">Failed</span>
                                  )}
                                </td>

                                <td>
                                  {hasRefunds ? (
                                    <button
                                      type="button"
                                      className="btn btn-ghost btn-sm"
                                      onClick={() => setExpandedPaymentUuid(isExpanded ? null : item.uuid)}
                                      style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '12px', color: 'var(--accent-primary)' }}
                                    >
                                      <span>{item.refunds.length} Refund{item.refunds.length > 1 ? 's' : ''}</span>
                                      <ChevronDown size={14} style={{ transform: isExpanded ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s' }} />
                                    </button>
                                  ) : (
                                    <span className="text-muted" style={{ fontSize: '12px' }}>—</span>
                                  )}
                                </td>

                                <td>
                                  <button
                                    type="button"
                                    className="btn btn-secondary btn-sm"
                                    onClick={() => handleDownloadInvoice(item)}
                                    disabled={downloadingInvoiceId === (item.uuid || item.id)}
                                    style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '12px', whiteSpace: 'nowrap' }}
                                    title={item.status === 'SUCCESS' ? 'Download Paid Receipt' : 'Download Payment Statement'}
                                  >
                                    {downloadingInvoiceId === (item.uuid || item.id) ? (
                                      <>
                                        <RefreshCw size={13} className="animate-spin" />
                                        <span>Loading...</span>
                                      </>
                                    ) : (
                                      <>
                                        <Download size={13} />
                                        <span>{item.status === 'SUCCESS' ? 'Download Receipt' : 'Download Statement'}</span>
                                      </>
                                    )}
                                  </button>
                                </td>
                              </tr>

                              {/* Expandable Refund Details Row */}
                              {isExpanded && hasRefunds && (
                                <tr style={{ background: 'var(--bg-surface-elevated)' }}>
                                  <td colSpan="7" style={{ padding: '12px 20px' }}>
                                    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                      <strong style={{ fontSize: '12px', color: 'var(--accent-primary)' }}>
                                        Associated Refunds ({item.refunds.length}):
                                      </strong>
                                      {item.refunds.map(rf => (
                                        <div
                                          key={rf.uuid || rf.id}
                                          style={{
                                            display: 'flex',
                                            justifyContent: 'space-between',
                                            alignItems: 'center',
                                            padding: '8px 12px',
                                            background: 'var(--bg-input)',
                                            borderRadius: '6px',
                                            border: '1px solid var(--border-subtle)',
                                            fontSize: '12px'
                                          }}
                                        >
                                          <div>
                                            <span style={{ fontWeight: 600 }}>Refund ID: </span>
                                            <span style={{ fontFamily: 'monospace' }}>{rf.razorpayRefundId || rf.uuid}</span>
                                            <span className="text-muted" style={{ marginLeft: '12px' }}>
                                              {rf.createdAt ? new Date(rf.createdAt).toLocaleDateString() : ''}
                                            </span>
                                          </div>
                                          <div>
                                            <span style={{ color: '#f97316', fontWeight: 700, marginRight: '12px' }}>
                                              -₹{((rf.amount || 0) / 100).toFixed(2)}
                                            </span>
                                            <span className="badge badge-success" style={{ fontSize: '10px' }}>
                                              {rf.status}
                                            </span>
                                          </div>
                                        </div>
                                      ))}
                                    </div>
                                  </td>
                                </tr>
                              )}
                            </React.Fragment>
                          );
                        })
                      )}
                    </tbody>
                  </table>
                </div>

                {/* Pagination */}
                {paymentHistory.totalPages > 1 && (
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '20px', paddingTop: '16px', borderTop: '1px solid var(--border-subtle)' }}>
                    <span className="text-muted" style={{ fontSize: '12px' }}>
                      Showing page {paymentHistory.currentPage + 1} of {paymentHistory.totalPages} ({paymentHistory.totalElements} records)
                    </span>
                    <div style={{ display: 'flex', gap: '8px' }}>
                      <button
                        type="button"
                        className="btn btn-secondary btn-sm"
                        disabled={paymentHistory.isFirst}
                        onClick={() => {
                          const prev = Math.max(0, paymentHistory.currentPage - 1);
                          setPaymentHistoryPage(prev);
                          loadPaymentHistory(prev, paymentHistoryType, paymentHistoryStatus, paymentHistorySearch);
                        }}
                      >
                        Previous
                      </button>
                      <button
                        type="button"
                        className="btn btn-secondary btn-sm"
                        disabled={paymentHistory.isLast || !paymentHistory.hasNext}
                        onClick={() => {
                          const next = paymentHistory.currentPage + 1;
                          setPaymentHistoryPage(next);
                          loadPaymentHistory(next, paymentHistoryType, paymentHistoryStatus, paymentHistorySearch);
                        }}
                      >
                        Next
                      </button>
                    </div>
                  </div>
                )}
              </div>
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
                  <label className="form-label">Aadhaar Number (UIDAI)</label>
                  <input
                    type="text"
                    className="form-input"
                    disabled
                    readOnly
                    value={currentUser?.aadhaarLast4 ? `XXXX XXXX ${currentUser.aadhaarLast4}` : 'Not Linked'}
                    style={{
                      background: 'var(--bg-subtle)',
                      color: 'var(--text-muted)',
                      fontFamily: 'monospace',
                      letterSpacing: '1px',
                      cursor: 'not-allowed'
                    }}
                  />
                  <small className="text-muted" style={{ display: 'block', marginTop: '4px', fontSize: '12px' }}>
                    Aadhaar numbers are securely encrypted and masked.
                  </small>
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

      {/* Bank Account Details Modal */}
      {activeModal === 'paymentProfile' && (
        <div className="modal-backdrop open">
          <div className="modal-card" style={{ maxWidth: '500px' }}>
            <div className="modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <div style={{ width: '36px', height: '36px', borderRadius: '8px', background: 'var(--accent-subtle)', color: 'var(--accent-primary)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <Landmark size={20} />
                </div>
                <div>
                  <h3 style={{ margin: 0, fontSize: '18px', fontWeight: 700 }}>Bank Account Details</h3>
                </div>
              </div>
              <button className="btn-close" onClick={() => setActiveModal(null)}>&times;</button>
            </div>

            {paymentProfile && !isEditingPaymentProfile ? (
              <div className="modal-body">
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px', padding: '18px', background: 'var(--bg-surface-elevated)', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-medium)' }}>
                  <div>
                    <span className="text-subtle" style={{ fontSize: '11px', display: 'block', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '4px' }}>Account Holder</span>
                    <strong style={{ fontSize: '14px', color: 'var(--text-main)' }}>{paymentProfile.accountHolderName}</strong>
                  </div>
                  <div>
                    <span className="text-subtle" style={{ fontSize: '11px', display: 'block', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '4px' }}>Bank Name</span>
                    <strong style={{ fontSize: '14px', color: 'var(--text-main)' }}>{paymentProfile.bankName}</strong>
                  </div>
                  <div>
                    <span className="text-subtle" style={{ fontSize: '11px', display: 'block', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '4px' }}>Account Number</span>
                    <strong style={{ fontSize: '14px', color: 'var(--text-main)', letterSpacing: '1px' }}>{paymentProfile.maskedAccountNumber || '••••••••'}</strong>
                  </div>
                  <div>
                    <span className="text-subtle" style={{ fontSize: '11px', display: 'block', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '4px' }}>IFSC Code</span>
                    <strong style={{ fontSize: '14px', color: 'var(--text-main)' }}>{paymentProfile.ifscCode}</strong>
                  </div>
                </div>

                <div className="modal-footer" style={{ marginTop: '20px', padding: '16px 0 0 0', background: 'transparent' }}>
                  <button
                    type="button"
                    className="btn btn-ghost"
                    style={{ color: '#ef4444' }}
                    onClick={handleDeletePaymentProfile}
                  >
                    <Trash2 size={14} style={{ marginRight: '6px' }} />
                    Delete Account
                  </button>
                  <button
                    type="button"
                    className="btn btn-primary"
                    onClick={() => openPaymentProfileModal(true)}
                  >
                    <Edit2 size={14} style={{ marginRight: '6px' }} />
                    Update Details
                  </button>
                </div>
              </div>
            ) : (
              <form onSubmit={handleSavePaymentProfile}>
                <div className="modal-body">
                  <div className="form-group mb-3">
                    <label className="form-label">
                      Account Holder Name <span style={{ color: 'var(--accent-primary)' }}>*</span>
                    </label>
                    <input
                      type="text"
                      className="form-input"
                      placeholder="e.g. JOHN DOE"
                      required
                      value={paymentProfileForm.accountHolderName}
                      onChange={e => setPaymentProfileForm({ ...paymentProfileForm, accountHolderName: e.target.value.toUpperCase() })}
                    />
                    <small className="text-muted" style={{ fontSize: '11px' }}>
                      Name as registered with your bank account
                    </small>
                  </div>

                  <div className="form-group mb-3">
                    <label className="form-label">
                      Bank Name <span style={{ color: 'var(--accent-primary)' }}>*</span>
                    </label>
                    <input
                      type="text"
                      className="form-input"
                      placeholder="e.g. HDFC Bank, State Bank of India, ICICI Bank"
                      required
                      value={paymentProfileForm.bankName}
                      onChange={e => setPaymentProfileForm({ ...paymentProfileForm, bankName: e.target.value })}
                    />
                  </div>

                  <div className="form-grid-2 mb-3">
                    <div className="form-group">
                      <label className="form-label">
                        Account Number <span style={{ color: 'var(--accent-primary)' }}>*</span>
                      </label>
                      <input
                        type="text"
                        className="form-input"
                        placeholder="e.g. 123456789012"
                        required
                        value={paymentProfileForm.accountNumber}
                        onChange={e => setPaymentProfileForm({ ...paymentProfileForm, accountNumber: e.target.value.replace(/[^\d]/g, '') })}
                      />
                    </div>
                    <div className="form-group">
                      <label className="form-label">
                        IFSC Code <span style={{ color: 'var(--accent-primary)' }}>*</span>
                      </label>
                      <input
                        type="text"
                        className="form-input"
                        placeholder="e.g. HDFC0001234"
                        required
                        maxLength={11}
                        value={paymentProfileForm.ifscCode}
                        onChange={e => setPaymentProfileForm({ ...paymentProfileForm, ifscCode: formatIfsc(e.target.value) })}
                      />
                    </div>
                  </div>

                  {paymentProfileForm.formError && (
                    <div style={{ padding: '10px 14px', borderRadius: '8px', background: 'rgba(239, 68, 68, 0.1)', border: '1px solid rgba(239, 68, 68, 0.3)', color: '#ef4444', fontSize: '13px', marginTop: '10px' }}>
                      {paymentProfileForm.formError}
                    </div>
                  )}
                </div>

                <div className="modal-footer">
                  <button
                    type="button"
                    className="btn btn-ghost"
                    onClick={() => {
                      if (paymentProfile) {
                        setIsEditingPaymentProfile(false);
                      } else {
                        setActiveModal(null);
                      }
                    }}
                    disabled={isSavingPaymentProfile}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="btn btn-primary"
                    disabled={isSavingPaymentProfile}
                  >
                    {isSavingPaymentProfile ? (
                      <>
                        <RefreshCw className="animate-spin" size={16} />
                        <span>Saving Details...</span>
                      </>
                    ) : (
                      'Save Bank Details'
                    )}
                  </button>
                </div>
              </form>
            )}
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
