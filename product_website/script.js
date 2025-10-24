// Apple/Tesla-inspired JavaScript for VortexGuru

// DOM Elements
const toggle = document.querySelector('.menu-toggle');
const mobileNav = document.querySelector('#mobile-nav');
const navLinks = document.querySelectorAll('.main-nav a, #mobile-nav a:not(.btn)');
const yearEl = document.querySelector('#year');

// Mobile navigation toggle
if (toggle) {
  toggle.addEventListener('click', () => {
    const expanded = toggle.getAttribute('aria-expanded') === 'true';
    toggle.setAttribute('aria-expanded', String(!expanded));
    mobileNav.classList.toggle('open');
    
    // Animate hamburger menu
    const spans = toggle.querySelectorAll('span');
    if (!expanded) {
      spans[0].style.transform = 'rotate(45deg) translate(5px, 5px)';
      spans[1].style.opacity = '0';
      spans[2].style.transform = 'rotate(-45deg) translate(7px, -6px)';
    } else {
      spans[0].style.transform = 'none';
      spans[1].style.opacity = '1';
      spans[2].style.transform = 'none';
    }
  });
}

// Close mobile nav on navigation
navLinks.forEach((link) => {
  link.addEventListener('click', () => {
    mobileNav.classList.remove('open');
    toggle?.setAttribute('aria-expanded', 'false');
    
    // Reset hamburger menu
    const spans = toggle?.querySelectorAll('span');
    if (spans) {
      spans[0].style.transform = 'none';
      spans[1].style.opacity = '1';
      spans[2].style.transform = 'none';
    }
  });
});

// Smooth scroll for anchor links
document.querySelectorAll('a[href^="#"]').forEach(anchor => {
  anchor.addEventListener('click', function (e) {
    e.preventDefault();
    const target = document.querySelector(this.getAttribute('href'));
    if (target) {
      const headerHeight = document.querySelector('.site-header').offsetHeight;
      const targetPosition = target.offsetTop - headerHeight - 20;
      
      window.scrollTo({
        top: targetPosition,
        behavior: 'smooth'
      });
    }
  });
});

// Highlight nav link based on scroll position
const sectionObserver = new IntersectionObserver(
  (entries) => {
    entries.forEach((entry) => {
      const id = entry.target.getAttribute('id');
      if (entry.isIntersecting) {
        // Remove active class from all nav links
        document.querySelectorAll('.main-nav a, #mobile-nav a').forEach(link => {
          link.classList.remove('active');
        });
        
        // Add active class to current section's nav link
        document.querySelectorAll(`a[href="#${id}"]`).forEach(link => {
          link.classList.add('active');
        });
      }
    });
  },
  { 
    threshold: 0.3,
    rootMargin: '-80px 0px -80px 0px'
  }
);

// Observe all sections
document.querySelectorAll('main section').forEach((section) => {
  sectionObserver.observe(section);
});

// Parallax effect for hero background orbs
window.addEventListener('scroll', () => {
  const scrolled = window.pageYOffset;
  const parallaxElements = document.querySelectorAll('.gradient-orb');
  
  parallaxElements.forEach((element, index) => {
    const speed = 0.5 + (index * 0.1);
    element.style.transform = `translateY(${scrolled * speed}px)`;
  });
});

// Intersection Observer for animations
const animationObserver = new IntersectionObserver(
  (entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.style.opacity = '1';
        entry.target.style.transform = 'translateY(0)';
      }
    });
  },
  { threshold: 0.1 }
);

// Add animation classes to elements
document.addEventListener('DOMContentLoaded', () => {
  // Animate cards
  const cards = document.querySelectorAll('.experience-card, .step-card, .use-case-card, .tech-highlight');
  cards.forEach((card, index) => {
    card.style.opacity = '0';
    card.style.transform = 'translateY(30px)';
    card.style.transition = `opacity 0.6s ease ${index * 0.1}s, transform 0.6s ease ${index * 0.1}s`;
    animationObserver.observe(card);
  });
  
  // Animate hero elements
  const heroElements = document.querySelectorAll('.hero-badge, .hero-title, .hero-description, .hero-actions');
  heroElements.forEach((element, index) => {
    element.style.opacity = '0';
    element.style.transform = 'translateY(30px)';
    element.style.transition = `opacity 0.8s ease ${index * 0.2}s, transform 0.8s ease ${index * 0.2}s`;
    animationObserver.observe(element);
  });
  
  // Animate device showcase
  const deviceShowcase = document.querySelector('.device-showcase');
  if (deviceShowcase) {
    deviceShowcase.style.opacity = '0';
    deviceShowcase.style.transform = 'translateY(50px) scale(0.95)';
    deviceShowcase.style.transition = 'opacity 1s ease 0.5s, transform 1s ease 0.5s';
    animationObserver.observe(deviceShowcase);
  }
});

// Form handling
const contactForm = document.querySelector('.contact-form');
if (contactForm) {
  contactForm.addEventListener('submit', (e) => {
    e.preventDefault();
    
    // Get form data
    const formData = new FormData(contactForm);
    const data = Object.fromEntries(formData);
    
    // Simple validation
    if (!data.name || !data.email) {
      alert('Please fill in all required fields.');
      return;
    }
    
    // Simulate form submission
    const submitBtn = contactForm.querySelector('button[type="submit"]');
    const originalText = submitBtn.textContent;
    
    submitBtn.textContent = 'Sending...';
    submitBtn.disabled = true;
    
    setTimeout(() => {
      submitBtn.textContent = 'Demo Requested!';
      submitBtn.style.background = 'linear-gradient(135deg, #007aff 0%, #5856d6 100%)';
      
      setTimeout(() => {
        submitBtn.textContent = originalText;
        submitBtn.disabled = false;
        submitBtn.style.background = '';
        contactForm.reset();
      }, 2000);
    }, 1500);
  });
}

// Play button interaction
const playButton = document.querySelector('.play-button');
if (playButton) {
  playButton.addEventListener('click', () => {
    // Add ripple effect
    const ripple = document.createElement('div');
    ripple.style.position = 'absolute';
    ripple.style.borderRadius = '50%';
    ripple.style.background = 'rgba(255, 255, 255, 0.3)';
    ripple.style.transform = 'scale(0)';
    ripple.style.animation = 'ripple 0.6s linear';
    ripple.style.left = '50%';
    ripple.style.top = '50%';
    ripple.style.width = '100px';
    ripple.style.height = '100px';
    ripple.style.marginLeft = '-50px';
    ripple.style.marginTop = '-50px';
    
    playButton.style.position = 'relative';
    playButton.appendChild(ripple);
    
    setTimeout(() => {
      ripple.remove();
    }, 600);
    
    // Simulate video play
    const avatar = document.querySelector('.volumetric-avatar');
    if (avatar) {
      avatar.style.animation = 'pulse 1s ease-in-out infinite';
      setTimeout(() => {
        avatar.style.animation = 'pulse 3s ease-in-out infinite';
      }, 2000);
    }
  });
}

// Add ripple animation CSS
const style = document.createElement('style');
style.textContent = `
  @keyframes ripple {
    to {
      transform: scale(4);
      opacity: 0;
    }
  }
`;
document.head.appendChild(style);

// Dynamic year
if (yearEl) {
  yearEl.textContent = new Date().getFullYear();
}

// Performance optimization: Debounce scroll events
function debounce(func, wait) {
  let timeout;
  return function executedFunction(...args) {
    const later = () => {
      clearTimeout(timeout);
      func(...args);
    };
    clearTimeout(timeout);
    timeout = setTimeout(later, wait);
  };
}

// Optimized scroll handler
const optimizedScrollHandler = debounce(() => {
  const scrolled = window.pageYOffset;
  const parallaxElements = document.querySelectorAll('.gradient-orb');
  
  parallaxElements.forEach((element, index) => {
    const speed = 0.5 + (index * 0.1);
    element.style.transform = `translateY(${scrolled * speed}px)`;
  });
}, 10);

window.addEventListener('scroll', optimizedScrollHandler);

// Preload critical images and fonts
document.addEventListener('DOMContentLoaded', () => {
  // Preload fonts
  const fontLinks = [
    'https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap',
    'https://fonts.googleapis.com/css2?family=SF+Pro+Display:wght@300;400;500;600;700;800&display=swap'
  ];
  
  fontLinks.forEach(href => {
    const link = document.createElement('link');
    link.rel = 'preload';
    link.as = 'style';
    link.href = href;
    document.head.appendChild(link);
  });
});

// Add loading state management
window.addEventListener('load', () => {
  document.body.classList.add('loaded');
  
  // Trigger hero animations
  setTimeout(() => {
    const heroElements = document.querySelectorAll('.hero-badge, .hero-title, .hero-description, .hero-actions');
    heroElements.forEach((element, index) => {
      setTimeout(() => {
        element.style.opacity = '1';
        element.style.transform = 'translateY(0)';
      }, index * 200);
    });
  }, 100);
});

// Error handling
window.addEventListener('error', (e) => {
  console.error('JavaScript error:', e.error);
});

// Console welcome message
console.log(`
🚀 VortexGuru Website Loaded Successfully!
✨ Built with Apple/Tesla-inspired design
🎨 Featuring smooth animations and modern UI
📱 Fully responsive and optimized
`);