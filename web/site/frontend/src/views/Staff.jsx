import { useState, useEffect } from 'react';
import api from '../api/client';
import { Users, MessageCircle } from 'lucide-react';
import Loading from '../components/Loading';

export default function Staff() {
  const [staff, setStaff] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get('/staff').then(r => setStaff(r.data)).catch(() => {}).finally(() => setLoading(false));
  }, []);

  const roles = [...new Set(staff.map(s => s.role))];

  return (
    <div className="min-h-screen pt-24 pb-16">
      <div className="page-container">
        <div className="text-center mb-10 animate-slide-up">
          <div className="inline-flex p-4 bg-violet-500/10 border border-violet-500/20 rounded-2xl mb-4">
            <Users size={36} className="text-violet-400" />
          </div>
          <h1 className="section-title gradient-text">Состав команды</h1>
          <p className="text-gray-500">Люди, которые делают Foxaria лучше каждый день</p>
        </div>

        {loading ? <Loading size="sm" /> : staff.length === 0 ? (
          <div className="glass p-16 text-center">
            <Users size={56} className="text-gray-700 mx-auto mb-4" />
            <p className="text-gray-400 text-xl font-semibold">Состав не добавлен</p>
            <p className="text-gray-600 text-sm mt-2">Администратор может добавить участников через панель управления</p>
          </div>
        ) : (
          <div className="space-y-12">
            {roles.map(role => (
              <div key={role}>
                <div className="flex items-center gap-4 mb-6">
                  <div className="gradient-divider flex-1" />
                  <h2 className="text-xl font-bold text-white whitespace-nowrap px-4">{role}</h2>
                  <div className="gradient-divider flex-1" />
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5 stagger">
                  {staff.filter(s => s.role === role).map(member => (
                    <StaffCard key={member.id} member={member} />
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function StaffCard({ member }) {
  return (
    <div className="glass p-6 text-center hover:border-violet-500/25 transition-all duration-300 group animate-slide-up">
      <div className="relative inline-block mb-4">
        {member.avatar ? (
          <img
            src={member.avatar}
            alt={member.name}
            className="w-20 h-20 rounded-2xl mx-auto group-hover:scale-105 transition-transform duration-300"
            style={{ imageRendering: member.avatar.includes('mc-heads') ? 'pixelated' : 'auto' }}
            onError={e => { e.target.src = `https://mc-heads.net/avatar/${member.name}/80`; }}
          />
        ) : (
          <div className="w-20 h-20 rounded-2xl mx-auto bg-gradient-main flex items-center justify-center text-white font-black text-2xl">
            {member.name[0]}
          </div>
        )}
        <div
          className="absolute -bottom-1 -right-1 w-5 h-5 rounded-full border-2 border-dark-800"
          style={{ background: member.role_color || '#FF6B35' }}
        />
      </div>

      <h3 className="font-bold text-white text-lg">{member.name}</h3>
      <p className="text-sm font-semibold mt-1" style={{ color: member.role_color || '#FF6B35' }}>
        {member.role}
      </p>

      {member.description && (
        <p className="text-gray-500 text-xs mt-3 leading-relaxed line-clamp-3">{member.description}</p>
      )}

      {/* Social links */}
      <div className="flex justify-center gap-2 mt-4">
        {member.discord && (
          <a href={`https://discord.com/users/${member.discord}`} target="_blank" rel="noreferrer"
            className="w-9 h-9 rounded-xl bg-[#5865F2]/15 hover:bg-[#5865F2]/30 flex items-center justify-center text-[#5865F2] transition-all text-xs font-bold">DC</a>
        )}
        {member.vk && (
          <a href={`https://vk.com/${member.vk}`} target="_blank" rel="noreferrer"
            className="w-9 h-9 rounded-xl bg-[#0077FF]/15 hover:bg-[#0077FF]/30 flex items-center justify-center text-[#0077FF] transition-all text-xs font-bold">VK</a>
        )}
        {member.telegram && (
          <a href={`https://t.me/${member.telegram}`} target="_blank" rel="noreferrer"
            className="w-9 h-9 rounded-xl bg-[#229ED9]/15 hover:bg-[#229ED9]/30 flex items-center justify-center text-[#229ED9] transition-all text-xs font-bold">TG</a>
        )}
      </div>
    </div>
  );
}
