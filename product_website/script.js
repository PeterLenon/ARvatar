const toggle = document.querySelector('.menu-toggle');
const mobileNav = document.querySelector('#mobile-nav');
const navLinks = document.querySelectorAll('.main-nav a, #mobile-nav a:not(.btn)');
const steps = document.querySelectorAll('.story-step');
const detailLabel = document.querySelector('[data-label]');
const detailDescription = document.querySelector('[data-description]');
const slides = document.querySelectorAll('.slide');
const prevBtn = document.querySelector('.slider-btn.prev');
const nextBtn = document.querySelector('.slider-btn.next');
const yearEl = document.querySelector('#year');

// Mobile navigation toggle
if (toggle) {
  toggle.addEventListener('click', () => {
    const expanded = toggle.getAttribute('aria-expanded') === 'true';
    toggle.setAttribute('aria-expanded', String(!expanded));
    mobileNav.classList.toggle('open');
  });
}

// Close mobile nav on navigation
navLinks.forEach((link) => {
  link.addEventListener('click', () => {
    mobileNav.classList.remove('open');
    toggle?.setAttribute('aria-expanded', 'false');
  });
});

// Highlight nav link based on scroll position
const sectionObserver = new IntersectionObserver(
  (entries) => {
    entries.forEach((entry) => {
      const id = entry.target.getAttribute('id');
      if (entry.isIntersecting) {
        document
          .querySelectorAll(`a[href="#${id}"]`)
          .forEach((link) => link.classList.add('active'));
      } else {
        document
          .querySelectorAll(`a[href="#${id}"]`)
          .forEach((link) => link.classList.remove('active'));
      }
    });
  },
  { threshold: 0.45 }
);

document.querySelectorAll('main section').forEach((section) => {
  sectionObserver.observe(section);
});

// Storyboard interactions
steps.forEach((step) => {
  step.addEventListener('mouseenter', () => updateStoryboard(step));
  step.addEventListener('focus', () => updateStoryboard(step));
  step.addEventListener('click', () => updateStoryboard(step));
});

function updateStoryboard(step) {
  steps.forEach((item) => item.classList.remove('active'));
  step.classList.add('active');
  detailLabel.textContent = `${step.dataset.step}. ${step.querySelector('h3').textContent.replace(/^\d+\.\s*/, '')}`;
  detailDescription.textContent = step.querySelector('p').textContent;
}

if (steps.length) {
  updateStoryboard(steps[0]);
}

// Case study slider
let activeIndex = 0;

function showSlide(index) {
  slides.forEach((slide, i) => {
    slide.classList.toggle('active', i === index);
  });
  activeIndex = index;
}

prevBtn?.addEventListener('click', () => {
  const nextIndex = (activeIndex - 1 + slides.length) % slides.length;
  showSlide(nextIndex);
});

nextBtn?.addEventListener('click', () => {
  const nextIndex = (activeIndex + 1) % slides.length;
  showSlide(nextIndex);
});

if (slides.length) {
  showSlide(0);
}

// Auto-advance slides
setInterval(() => {
  if (!document.hidden && slides.length) {
    showSlide((activeIndex + 1) % slides.length);
  }
}, 6000);

// Dynamic year
if (yearEl) {
  yearEl.textContent = new Date().getFullYear();
}
