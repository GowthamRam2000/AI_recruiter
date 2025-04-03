document.addEventListener('DOMContentLoaded', function () {
    console.log("Custom JS Loaded - Initializing dynamic actions...");
    function showFeedback(message, type='info', areaId='message-area-main') {
        const area=document.getElementById(areaId);
        if (!area) {
            console.error("Feedback area not found:", areaId);
            return;
        }
        const alertType=type === 'error' ? 'danger' : type;
        const alertHtml=`
            <div class="alert alert-${alertType} alert-dismissible fade show mt-3" role="alert">
                ${message}
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            </div>`;
        area.innerHTML=alertHtml;
    }
    function setLoadingState(button, isLoading) {
        if (!button) return;
        if (isLoading) {
            button.disabled=true;
            if (!button.dataset.originalText) {
                button.dataset.originalText=button.innerHTML;
            }
            button.innerHTML=`<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Processing...`;
        } else {
            button.disabled=false;
            button.innerHTML=button.dataset.originalText || 'Action Complete';
            delete button.dataset.originalText;
        }
    }
    async function handleApiResponse(response) {
        const contentType=response.headers.get("content-type");
        let body;
        try {
            if (contentType && contentType.includes("application/json")) {
                body=await response.json();
            } else {
                const text=await response.text();
                body={ message: text || (response.ok ? "Request processed." : `Server error: ${response.statusText}`) };
            }
        } catch (error) {
            console.error("Error parsing response body:", error);
            body={ message: `Error parsing server response. Status: ${response.status}` };
            return { ok: false, status: response.status, body: body };
        }
        return { ok: response.ok, status: response.status, body: body };
    }
    const uploadJobForm=document.getElementById('upload-job-form');
    if (uploadJobForm) {
        uploadJobForm.addEventListener('submit', function (event) {
            event.preventDefault();
            const formData=new FormData(this);
            const feedbackArea=document.getElementById('upload-job-feedback');
            const submitButton=this.querySelector('button[type="submit"]');
            const modalElement=document.getElementById('uploadJobModal');
            const modal=bootstrap.Modal.getInstance(modalElement) || new bootstrap.Modal(modalElement);
            setLoadingState(submitButton, true);
            if(feedbackArea) feedbackArea.innerHTML='';
            fetch('/api/jobs/load-csv', { method: 'POST', body: formData })
                .then(handleApiResponse)
                .then(({ ok, status, body }) => {
                    const message=body.message || (ok ? 'Upload successful!' : `Error: Status ${status}`);
                    if (ok) {
                        if(feedbackArea) showFeedback(`Success: ${message}`, 'success', feedbackArea.id);
                        setTimeout(() => {
                            if(modal) modal.hide();
                            showFeedback(`Successfully loaded ${body.count || 0} jobs. Page reloading...`, 'success', 'message-area-jobs');
                            setTimeout(() => window.location.reload(), 1000);
                        }, 1500);
                    } else {
                        if(feedbackArea) showFeedback(`Error: ${message}`, 'error', feedbackArea.id);
                        setLoadingState(submitButton, false);
                    }
                })
                .catch(error => {
                    console.error('Fetch Error (Job Upload):', error);
                    if(feedbackArea) showFeedback('Network error or server not responding during job upload.', 'error', feedbackArea.id);
                    setLoadingState(submitButton, false);
                });
        });
    }
    const uploadCvForm=document.getElementById('upload-cv-form');
    if (uploadCvForm) {
        uploadCvForm.addEventListener('submit', function (event) {
            event.preventDefault();
            const fileInput=document.getElementById('cvFiles');
            const files=fileInput.files;
            const feedbackArea=document.getElementById('upload-cv-feedback');
            const submitButton=this.querySelector('button[type="submit"]');
            const modalElement=document.getElementById('uploadCvModal');
            const modal=bootstrap.Modal.getInstance(modalElement) || new bootstrap.Modal(modalElement);
            const totalFiles=files.length; // Define totalFiles here
            if (files.length === 0) {
                if(feedbackArea) showFeedback('Please select at least one PDF file.', 'warning', feedbackArea.id);
                return;
            }
            setLoadingState(submitButton, true);
            if(feedbackArea) feedbackArea.innerHTML=`<p>Uploading ${files.length} file(s)...</p>`;
            let promises=Array.from(files).map(file => {
                const formData=new FormData();
                formData.append('file', file);
                return fetch('/api/candidates/upload', { method: 'POST', body: formData })
                    .then(handleApiResponse)
                    .then(result => ({ ...result, fileName: file.name }))
                    .catch(error => {
                        console.error(`Workspace Error (CV Upload - ${file.name}):`, error);
                        return { ok: false, status: 0, body: { message: 'Network error or server unreachable.' }, fileName: file.name, error: error };
                    });
            });

            Promise.allSettled(promises)
                .then(results => {
                    let finalMessages=[];
                    let successCount=0;
                    let errorCount=0;

                    results.forEach(result => {
                        if (result.status === 'fulfilled') {
                            const { ok, status, body, fileName }=result.value;
                            const message=body.message || `Processing ${fileName}`;
                            if (ok) {
                                successCount++;
                                finalMessages.push(`<div class="text-success small">OK: ${fileName} - ${message}</div>`);
                            } else {
                                errorCount++;
                                finalMessages.push(`<div class="text-danger small">Failed: ${fileName} - ${message} (Status: ${status})</div>`);
                            }
                        } else {
                            errorCount++;
                            console.error(`Promise Rejected for CV Upload:`, result.reason);
                            finalMessages.push(`<div class="text-danger small">Failed: Network error during upload.</div>`);
                        }
                    });

                    if(feedbackArea) {
                        feedbackArea.innerHTML=`<h6>Upload Complete</h6><p>Success: ${successCount}, Errors: ${errorCount}</p>${finalMessages.join('')}`;
                    }

                    showFeedback(`CV Upload finished. Success: ${successCount}, Errors: ${errorCount}. Page will reload shortly...`, 'info', 'message-area-cvs');
                    setTimeout(() => {
                        window.location.reload();
                    }, 2500);

                })
                .finally(() => {
                });
        });
    }
    const contentArea=document.querySelector('main.container');
    if (contentArea) {
        contentArea.addEventListener('click', function(event) {
            const button=event.target.closest('button[data-job-id]');
            if (!button) return;
            const jobId=button.dataset.jobId;
            const action=button.classList;
            let apiUrl='';
            let method='POST';
            let successMessage='';
            let errorMessage='';
            let infoMessage='';
            let feedbackAreaId='message-area-jobs';
            if (action.contains('summarize-job-btn')) {
                apiUrl=`/api/jobs/${jobId}/summarize`;
                successMessage=`Job ${jobId} submitted for summarization.`;
                errorMessage=`Error summarizing Job ${jobId}.`;
                infoMessage=`Summarizing Job ${jobId}...`;
            } else if (action.contains('match-all-btn')) {
                apiUrl=`/api/workflow/match-all?jobId=${jobId}`;
                successMessage=`Matching initiated for Job ${jobId}. Check details page later.`;
                errorMessage=`Error initiating matching for Job ${jobId}.`;
                infoMessage=`Initiating matching for Job ${jobId}...`;
            } else if (action.contains('shortlist-btn')) {
                const thresholdInput=prompt("Enter shortlisting score threshold (0-100):", "80");
                const threshold=parseFloat(thresholdInput);
                if (isNaN(threshold) || threshold < 0 || threshold > 100) {
                    showFeedback('Invalid threshold value.', 'warning', feedbackAreaId);
                    return;
                }
                apiUrl=`/api/workflow/shortlist?jobId=${jobId}&threshold=${threshold}`;
                successMessage=`Shortlisting request sent for Job ${jobId}.`;
                errorMessage=`Error during shortlisting for Job ${jobId}.`;
                infoMessage=`Processing shortlist for Job ${jobId}...`;
            } else if (action.contains('send-invites-btn')) {
                console.log(`Send Invites clicked for Job ID: ${jobId}`);
                apiUrl=`/api/workflow/send-interviews?jobId=${jobId}`;
                successMessage=`Successfully processed 'Send Invites' request for Job ${jobId}.`;
                errorMessage=`Error processing 'Send Invites' for Job ${jobId}.`;
                infoMessage=`Attempting to send invites for Job ${jobId}...`;
            } else {
                return;
            }
            console.log(`Action Button Clicked: ${button.textContent.trim()} for Job ID: ${jobId}`);
            setLoadingState(button, true);
            if (infoMessage) showFeedback(infoMessage, 'info', feedbackAreaId);

            fetch(apiUrl, { method: method /* Add headers if needed */ })
                .then(handleApiResponse)
                .then(({ ok, status, body }) => {
                    const backendMessage=body.message || '';
                    if (ok) {
                        console.log(`Action Success (${apiUrl}):`, body);
                        let displayMessage=successMessage;
                        if (action.contains('summarize-job-btn') && body.status) {
                            displayMessage=`Job ${jobId} status updated to ${body.status}.`;
                            updateJobStatusBadge(button.closest('tr'), body.status);
                        } else if (action.contains('shortlist-btn') && body.shortlistedCount !== undefined) {
                            displayMessage=`Shortlisting complete for Job ${jobId}. Count: ${body.shortlistedCount}. View job details for status changes.`;
                        } else if (action.contains('send-invites-btn') && body.invitationsProcessed !== undefined) {
                            displayMessage=`Interview sending process completed for Job ${jobId}. Processed: ${body.invitationsProcessed}. Check logs for details.`;
                        } else if (backendMessage) {
                            displayMessage=backendMessage;
                        }
                        showFeedback(displayMessage, 'success', feedbackAreaId);
                    } else {
                        console.error(`Action Error (${apiUrl}):`, status, body);
                        showFeedback(`${errorMessage} ${backendMessage}`, 'error', feedbackAreaId);
                    }
                })
                .catch(error => {
                    console.error(`Workspace Error (${apiUrl}):`, error);
                    showFeedback(`Network error performing action for Job ${jobId}. Check application logs.`, 'error', feedbackAreaId);
                })
                .finally(() => {
                    setLoadingState(button, false);
                });
        });
    }


    function updateJobStatusBadge(tableRow, newStatus) {
        if (!tableRow) return;
        const statusCell=tableRow.querySelector('td:nth-child(3)');
        if (!statusCell) return;
        const badge=statusCell.querySelector('.badge');
        if (!badge) return;
        badge.className='badge fs-6';
        let badgeClass='text-bg-secondary';
        let badgeText=newStatus || 'UNKNOWN';
        if (newStatus === 'SUMMARIZED') badgeClass='text-bg-success';
        else if (newStatus === 'SUMMARIZING') badgeClass='text-bg-warning';
        else if (newStatus && newStatus.startsWith('ERROR')) badgeClass='text-bg-danger';
        else if (newStatus === 'NEW') badgeClass='text-bg-secondary';
        badge.classList.add(badgeClass);
        if (newStatus === 'SUMMARIZING') {
            badge.innerHTML=`<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Sum...`;
        } else {
            badge.textContent=badgeText;
        }
        const summarizeBtn=tableRow.querySelector('.summarize-job-btn');
        if (summarizeBtn) {
            summarizeBtn.style.display=(newStatus === 'NEW' || (newStatus && newStatus.startsWith('ERROR'))) ? 'inline-block' : 'none';
        }
    }
    function renderJsonAsDl(data, level=0) {
        const dl=document.createElement('dl');
        dl.className=level === 0 ? 'row mb-0' : 'ms-3';
        for (const key in data) {
            if (data.hasOwnProperty(key)) {
                const dt=document.createElement('dt');
                dt.className=level === 0 ? 'col-sm-3 fw-semibold pt-2' : 'fw-semibold pt-2';
                dt.textContent=key.replace(/([A-Z])/g, ' $1').replace(/_/g, ' ').replace(/^./, str => str.toUpperCase());
                const dd=document.createElement('dd');
                dd.className=level === 0 ? 'col-sm-9 pt-2' : 'mb-0';
                const value=data[key];
                if (typeof value === 'object' && value !== null) {
                    if (Array.isArray(value)) {
                        const ul=document.createElement('ul');
                        ul.className='list-unstyled ps-1 mb-0';
                        if (value.length === 0) {
                            const li=document.createElement('li');
                            li.innerHTML='<em class="text-muted small">None</em>';
                            ul.appendChild(li);
                        } else {
                            value.forEach(item => {
                                const li=document.createElement('li');
                                li.className='mb-1';
                                if (typeof item === 'object' && item !== null) {
                                    li.appendChild(renderJsonAsDl(item, level + 1));
                                } else {
                                    li.textContent=item === null || item === undefined || item === '' ? 'N/A' : item;
                                }
                                ul.appendChild(li);
                            });
                        }
                        dd.appendChild(ul);
                    } else {
                        dd.appendChild(renderJsonAsDl(value, level + 1));
                    }
                } else {
                    dd.textContent=(value === null || value === undefined || value === '') ? 'N/A' : value;
                }
                dl.appendChild(dt);
                dl.appendChild(dd);
                if(level === 0 && Object.keys(data).indexOf(key) < Object.keys(data).length - 1) {
                    const hr=document.createElement('hr');
                    hr.className='col-12 my-2 border-light-subtle';
                    dl.appendChild(hr);
                }
            }
        }
        return dl;
    }
    function applyJsonRendering(inputId, displayId) {
        const displayElement=document.getElementById(displayId);
        const rawJsonInput=document.getElementById(inputId);
        if (displayElement && rawJsonInput && rawJsonInput.value) {
            const preElement=displayElement.querySelector('pre');
            if(preElement) preElement.remove();
            const smallElement=displayElement.querySelector('small');
            if(smallElement) smallElement.remove();
            try {
                const jsonData=JSON.parse(rawJsonInput.value);
                displayElement.innerHTML='';
                displayElement.appendChild(renderJsonAsDl(jsonData));
            } catch (e) {
                console.error(`Failed to parse/render JSON for ${displayId}:`, e);
                const errorMsg=document.createElement('p');
                errorMsg.innerHTML=`<small class="text-danger">Could not parse stored JSON data for display.</small>`;
                if (!displayElement.querySelector('.text-danger')) {
                    const pre=document.createElement('pre');
                    pre.className='bg-light p-3 rounded border';
                    pre.textContent=rawJsonInput.value;
                    displayElement.innerHTML='';
                    displayElement.appendChild(pre);
                    displayElement.appendChild(errorMsg);
                }
            }
        } else if (displayElement) {
            console.debug(`JSON input or display area not found or empty for ${displayId}`);
        }
    }
    applyJsonRendering('raw-summary-json', 'summary-display');
    applyJsonRendering('raw-cv-json', 'cv-data-display');

});